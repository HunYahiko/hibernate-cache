package com.gringotts.hibernatecache.cacheentry;

import com.gringotts.hibernatecache.AbstractTestConfiguration;
import lombok.Data;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.junit.Test;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class QueryCacheView extends AbstractTestConfiguration {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Post.class,
        };
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put("hibernate.cache.use_query_cache", Boolean.TRUE.toString());
    }

    @Test
    public void queryCacheView() {
        doInJPA(entityManager -> {
            Post post1 = new Post();
            post1.setId(1L);
            post1.setTitle("Welcome to Hibernate Cache");

            entityManager.persist(post1);

            Post post2 = new Post();
            post2.setId(2L);
            post2.setTitle("Welcome to Hibernate");

            entityManager.persist(post2);
        });

        doInJPA(entityManager -> {
            List<Post> posts = entityManager
                    .createQuery("select p " +
                                         "from Post_Query p " +
                                         "where p.title like :titlePattern ")
                    .setParameter("titlePattern", "Welcome to%")
                    .setHint("org.hibernate.cacheable", true)
                    .getResultList();

            assertEquals(2, posts.size());
        });

        printQueryCacheRegionStatistics();

        doInJPA(entityManager -> {
            LOGGER.info("Load from cache");
            List<Post> posts = entityManager
                    .createQuery("select p " +
                                         "from Post_Query p " +
                                         "where p.title like :titlePattern ")
                    .setParameter("titlePattern", "Welcome to%")
                    .setHint("org.hibernate.cacheable", true)
                    .getResultList();

            assertEquals(2, posts.size());
        });

        printQueryCacheRegionStatistics();

        doInJPA(entityManager -> {
            List<Post> posts = entityManager
                    .createQuery("select p " +
                                         "from Post_Query p " +
                                         "where p.title like :titlePattern ")
                    .setParameter("titlePattern", "Welcome to%")
                    .unwrap(org.hibernate.query.Query.class)
                    .setCacheable(true)
                    .getResultList();

            assertEquals(2, posts.size());
        });

        printQueryCacheRegionStatistics();
    }

    @Entity(name = "Post_Query")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @Data
    public static class Post {

        @Id
        private Long id;
        private String title;
    }
}
