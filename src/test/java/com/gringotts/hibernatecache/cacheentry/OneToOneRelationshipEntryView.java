package com.gringotts.hibernatecache.cacheentry;

import com.gringotts.hibernatecache.AbstractTestConfiguration;
import com.gringotts.hibernatecache.domain.Post;
import com.gringotts.hibernatecache.domain.PostDetail;
import org.junit.Test;

import java.time.LocalDateTime;

public class OneToOneRelationshipEntryView extends AbstractTestConfiguration {

    @Test
    public void oneToOneRelationshipView() {
        doInJPA(entityManager -> {
            final var post = new Post();
            post.setId(1L);
            post.setTitle("Welcome to Hibernate Caching presentation");
            entityManager.persist(post);
            final var postDetail = new PostDetail();
            postDetail.setId(1L);
            postDetail.setCreatedBy("Mystery Man");
            postDetail.setCreatedOn(LocalDateTime.now());
            postDetail.setPost(post);
            entityManager.persist(postDetail);
        });

        printCacheRegionStatistics(Post.class.getName());
        printCacheRegionStatistics(PostDetail.class.getName());

        doInJPA(entityManager -> {
            entityManager.find(Post.class, 1L);
            final PostDetail postDetail = entityManager.find(PostDetail.class, 1L);
            postDetail.getPost();
        });

        // Count the cache hits on post + explanation + solution
        printCacheRegionStatistics(Post.class.getName());
        printCacheRegionStatistics(PostDetail.class.getName());
    }
}
