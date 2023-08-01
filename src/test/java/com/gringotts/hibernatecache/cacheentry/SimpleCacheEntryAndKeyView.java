package com.gringotts.hibernatecache.cacheentry;

import com.gringotts.hibernatecache.AbstractTestConfiguration;
import com.gringotts.hibernatecache.domain.Post;
import org.junit.Test;

public class SimpleCacheEntryAndKeyView extends AbstractTestConfiguration {

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
}
