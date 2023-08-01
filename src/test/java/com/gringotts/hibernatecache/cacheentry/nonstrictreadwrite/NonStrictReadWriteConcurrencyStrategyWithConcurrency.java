package com.gringotts.hibernatecache.cacheentry.nonstrictreadwrite;

import com.gringotts.hibernatecache.AbstractTestConfiguration;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.EmptyInterceptor;
import org.hibernate.Interceptor;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.junit.Test;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NonStrictReadWriteConcurrencyStrategyWithConcurrency extends AbstractTestConfiguration {


    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
                User.class
        };
    }

    private AtomicBoolean applyInterceptor = new AtomicBoolean();

    private final CountDownLatch endLatch = new CountDownLatch(1);

    private class SecondTransactionInterceptor extends EmptyInterceptor {
        @Override
        public void beforeTransactionCompletion(Transaction tx) {
            if(applyInterceptor.get()) {
                LOGGER.info("Fetch User from another transaction");
                assertFalse(sessionFactory().getCache()
                                            .containsEntity(User.class, 1L));
                executeSync(() -> {
                    EntityManager _entityManager = entityManagerFactory().createEntityManager();
                    User user =
                            _entityManager.find(User.class, 1L);
                    LOGGER.info("Cached User from with secondThread's transaction {}",
                                user);
                    _entityManager.close();
                    endLatch.countDown();
                });
                assertTrue(sessionFactory().getCache()
                                           .containsEntity(User.class, 1L));
            }
        }
    }

    @Override
    protected Interceptor interceptor() {
        return new SecondTransactionInterceptor();
    }


    @Override
    protected void afterInit() {
        doInJPA(entityManager -> {
            User user = new User("Hibernate-Cache-Pro");
            entityManager.persist(user);
        });
    }

    @Test
    public void userEntityUpdate() throws InterruptedException {
        doInJPA(entityManager -> {
            LOGGER.info("Load and modify User");
            User user = entityManager.find(User.class, 1L);
            assertTrue(sessionFactory().getCache()
                                       .containsEntity(User.class, 1L));
            user.setName("Hibernate Cache Loser");
            applyInterceptor.set(true);
        });
        endLatch.await();
        assertFalse(sessionFactory().getCache()
                                    .containsEntity(User.class, 1L));
        doInJPA(entityManager -> {
            applyInterceptor.set(false);
            User user = entityManager.find(User.class, 1L);
            LOGGER.info("Cached User {}", user);
        });
    }

    @Test
    public void userOptimisticLocking() {
        LOGGER.info("userOptimisticLocking");
        doInJPA(entityManager -> {
            LOGGER.info("Load User");
            User user = entityManager.find(User.class, 1L);
            entityManager.unwrap(Session.class).buildLockRequest(new LockOptions().setLockMode(LockMode.OPTIMISTIC)).lock(user);
        });
        doInJPA(entityManager -> {
            LOGGER.info("Load User again");
            entityManager.find(User.class, 1L);
        });
    }

    @Entity(name = "user")
    @Table(name = "cache_user")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @Data
    @NoArgsConstructor
    public static class User {

        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private Long id;

        private String name;

        @Version
        private short version;

        public User(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "User{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    '}';
        }
    }
}
