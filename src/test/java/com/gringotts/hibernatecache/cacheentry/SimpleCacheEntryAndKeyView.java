package com.gringotts.hibernatecache.cacheentry;

import com.gringotts.hibernatecache.AbstractTestConfiguration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.junit.Test;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.io.Serializable;

public class SimpleCacheEntryAndKeyView extends AbstractTestConfiguration {

    @Override
    protected Class<?>[] entities() {
        return new Class[]{Post.class};
    }

    @Test
    public void noRelationshipView() {
        doInJPA(entityManager -> {
            final var post = new Post();
            post.setId(1L);
            post.setTitle("Welcome to Hibernate Caching presentation");
            entityManager.persist(post);
        });
        printCacheRegionStatistics(Post.class.getName());
    }

    @Test
    public void noRelationshipViewCacheHit() {
        doInJPA(entityManager -> {
            final var post = new Post();
            post.setId(1L);
            post.setTitle("Welcome to Hibernate Caching presentation");
            entityManager.persist(post);
        });

        printCacheRegionStatistics(Post.class.getName());

        doInJPA(entityManager -> entityManager.find(Post.class, 1L));

        printCacheRegionStatistics(Post.class.getName());
    }

    @Entity(name = "Post_SimpleCacheEntry")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Post implements Serializable {

        @Id
        private Long id;

        private String title;
    }
}
