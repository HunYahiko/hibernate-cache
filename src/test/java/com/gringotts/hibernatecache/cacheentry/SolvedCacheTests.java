package com.gringotts.hibernatecache.cacheentry;

import com.gringotts.hibernatecache.AbstractTestConfiguration;
import com.gringotts.hibernatecache.domain.Post;
import com.gringotts.hibernatecache.domain.PostComment;
import com.gringotts.hibernatecache.domain.PostDetail;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class SolvedCacheTests extends AbstractTestConfiguration {

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

        printCacheRegionStatistics(Post.class.getName());
        printCacheRegionStatistics(PostDetail.class.getName());

        doInJPA(entityManager -> {
            final PostDetail postDetail = entityManager.find(PostDetail.class, 1L);
            postDetail.getPost().getTitle();
        });

        printCacheRegionStatistics(Post.class.getName());
        printCacheRegionStatistics(PostDetail.class.getName());
    }

    @Test
    public void oneToManyRelationshipView() {
        doInJPA(entityManager -> {
            final var post = new Post();
            post.setId(1L);
            post.setTitle("Welcome to Hibernate Caching presentation");
            entityManager.persist(post);
        });

        printCacheRegionStatistics(Post.class.getName());

        doInJPA(entityManager -> {
            final var post = entityManager.find(Post.class, 1L);
            post.addComment(new PostComment(1L, "This is one comment"));
            post.addComment(new PostComment(2L, "This is two comment"));
        });

        printCacheRegionStatistics(Post.class.getName());
        printCacheRegionStatistics(PostComment.class.getName());

        doInJPA(entityManager -> {
            final var post = entityManager.find(Post.class, 1L);
            assertThat(post.getComments().size()).isEqualTo(2);
        });

        printCacheRegionStatistics(Post.class.getName());
        printCacheRegionStatistics(PostComment.class.getName());
        printCollectionCacheRegionStatistics(Post.class, "comments");

        doInJPA(entityManager -> {
            final var post = entityManager.find(Post.class, 1L);
            assertThat(post.getComments().size()).isEqualTo(2);
        });

        printCollectionCacheRegionStatistics(Post.class, "comments");
    }
}
