package com.gringotts.hibernatecache.cacheentry.readwrite;

import com.gringotts.hibernatecache.AbstractTestConfiguration;
import com.gringotts.hibernatecache.ReflectionUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ehcache.core.Ehcache;
import org.hibernate.EmptyInterceptor;
import org.hibernate.Interceptor;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.cache.internal.DefaultCacheKeysFactory;
import org.hibernate.cache.spi.support.AbstractRegion;
import org.hibernate.cache.spi.support.StorageAccess;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.stat.CacheRegionStatistics;
import org.hibernate.stat.Statistics;
import org.junit.Test;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Version;
import java.io.Serializable;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Thread.sleep;

public class ReadWriteCacheConcurrencyStrategyWithLockTimeout extends AbstractTestConfiguration {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
                Repository.class
        };
    }

    private AtomicBoolean applyInterceptor = new AtomicBoolean();

    @Override
    protected Interceptor interceptor() {
        return new EmptyInterceptor() {
            @Override
            public void beforeTransactionCompletion(Transaction tx) {
                if(applyInterceptor.get()) {
                    tx.rollback();
                }
            }
        };
    }

    @Override
    protected Properties properties() {
        Properties properties = super.properties();
        properties.put("hibernate.cache.use_second_level_cache", Boolean.TRUE.toString());
        properties.put("hibernate.cache.region.factory_class", "jcache");
        properties.put("net.sf.ehcache.hibernate.cache_lock_timeout", String.valueOf(250));
        return properties;
    }

    @Override
    public void afterInit() {
        doInJPA(entityManager -> {
            Repository repository = new Repository("Hibernate-Cache");
            entityManager.persist(repository);
        });
    }

    @Test
    public void testRepositoryEntityUpdate() {
        try {
            doInJPA(entityManager -> {
                Repository repository = entityManager.find(Repository.class, 1L);
                repository.setName("Ehcache");
                applyInterceptor.set(true);
            });
        } catch (Exception e) {
            LOGGER.info("Expected", e);
        }
        applyInterceptor.set(false);

        AtomicReference<Object> previousCacheEntryReference = new AtomicReference<>();
        AtomicBoolean cacheEntryChanged = new AtomicBoolean();
        AtomicInteger numberOfIterations = new AtomicInteger(0);

        while (!cacheEntryChanged.get()) {
            doInJPA(entityManager -> {
                boolean entryChange;
                entityManager.find(Repository.class, 1L);
                try {
                    Object previousCacheEntry = previousCacheEntryReference.get();
                    Object cacheEntry = getCacheEntry(Repository.class, 1L);
                    entryChange = previousCacheEntry != null &&
                            !previousCacheEntry.equals(cacheEntry);
                    previousCacheEntryReference.set(cacheEntry);
                    LOGGER.info("Cache entry {}", ToStringBuilder.reflectionToString(cacheEntry));
                    if(!entryChange) {
                        numberOfIterations.incrementAndGet();
                        sleep(100);
                    } else {
                        LOGGER.info("Cache entry was unlocked");
                        LOGGER.info("Cache entry {}", ToStringBuilder.reflectionToString(getCacheEntry(Repository.class, 1L)));
                        LOGGER.info("Number of iterations it took: {}", numberOfIterations.get());
                        cacheEntryChanged.set(true);
                    }
                } catch (IllegalAccessException | InterruptedException e) {
                    LOGGER.error("Error accessing Cache", e);
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getCacheEntry(Class<T> clazz, Long id) throws IllegalAccessException {
        EntityPersister entityPersister = ((SessionFactoryImplementor) sessionFactory()).getEntityPersister(clazz.getName() );
        return (T) getCache(clazz).get(cacheKey(id, entityPersister));
    }

    private Ehcache getCache(Class clazz) {
        SessionFactory sessionFactory = sessionFactory();
        Statistics statistics = sessionFactory.getStatistics();
        if (sessionFactory.getSessionFactoryOptions().isQueryCacheEnabled()) {
            ReflectionUtils.invokeMethod(statistics, "getQueryRegionStats", "default-query-results-region");
        }

        final var regionName = clazz.getName();
        CacheRegionStatistics cacheRegionStatistics = statistics.getDomainDataRegionStatistics(regionName);
        AbstractRegion region = ReflectionUtils.getFieldValue(cacheRegionStatistics, "region");
        StorageAccess storageAccess = getStorageAccess(region);
        return getEhcache(storageAccess);
    }

    private Object cacheKey(Serializable identifier, EntityPersister p) {
        return DefaultCacheKeysFactory.INSTANCE.createEntityKey(
                identifier, p, (SessionFactoryImplementor) sessionFactory(), null
        );
    }

    @Entity(name = "repository")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @Data
    @NoArgsConstructor
    public static class Repository {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String name;

        @Version
        private int version;

        public Repository(String name) {
            this.name = name;
        }
    }
}
