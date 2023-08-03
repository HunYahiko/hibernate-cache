package com.gringotts.hibernatecache.cacheentry.readonly;

import com.gringotts.hibernatecache.AbstractTestConfiguration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.junit.Test;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ReadOnlyCacheConcurrencyStrategy extends AbstractTestConfiguration {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
                Post.class,
                PostComment.class
        };
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put("hibernate.cache.use_second_level_cache", Boolean.TRUE.toString());
    }

    public void afterInit() {
        doInJPA(entityManager -> {
            final var post = Post.builder().id(1L).title("Hibernate Caching").build();
            post.addComment(PostComment.builder().id(1L).review("JDBC part review").build());
            post.addComment(PostComment.builder().id(2L).review("Hibernate part review").build());
            entityManager.persist(post);
        });
        printEntityCacheRegionStatistics(Post.class);
        printEntityCacheRegionStatistics(PostComment.class);
        printCollectionCacheRegionStatistics(Post.class, "comments");

        LOGGER.info("Post entity inserted");
    }

    @Test
    public void testPostEntityLoad() {

        LOGGER.info("Entities are loaded from cache");

        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);
            printEntityCacheRegionStatistics(Post.class);
        });
    }

    @Test
    public void testCollectionCacheLoad() {
        LOGGER.info("Collections require separate caching");

        printCollectionCacheRegionStatistics(Post.class, "comments");

        doInJPA(entityManager -> {
            LOGGER.info("Load PostComment from database");
            Post post = entityManager.find(Post.class, 1L);
            assertEquals(2, post.getComments().size());
            printCollectionCacheRegionStatistics(Post.class, "comments");
        });

        printCacheRegionStatistics(Post.class.getName());
        printCacheRegionStatistics(PostComment.class.getName());

        doInJPA(entityManager -> {
            LOGGER.info("Load PostComment from cache");
            Post post = entityManager.find(Post.class, 1L);
            assertEquals(2, post.getComments().size());
        });

        printCollectionCacheRegionStatistics(Post.class, "comments");
    }

    @Test
    public void testCollectionCacheUpdate() {
        LOGGER.info("Collection cache entries cannot be updated");
        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);
            PostComment comment = post.getComments().remove(0);
            comment.setPost(null);
        });

        printCollectionCacheRegionStatistics(Post.class, "comments");
        printCacheRegionStatistics(PostComment.class.getName());

        try {
            doInJPA(entityManager -> {
                LOGGER.info("Load PostComment from cache");
                Post post = entityManager.find(Post.class, 1L);
                assertEquals(1, post.getComments().size());
            });
        } catch (Exception e) {
            LOGGER.error("Expected", e);
        }

        try {
            doInJPA(entityManager -> {
                LOGGER.info("Try to change a cached comment");
                Post post = entityManager.find(Post.class, 1L);
                PostComment comment = post.getComments().get(0);
                comment.setReview("This is a new review");
            });
        } catch (Exception e) {
            LOGGER.error("Expected", e);
        }
    }

    @Test
    public void testEntityUpdate() {
        try {
            LOGGER.info("Cache entries cannot be updated");
            doInJPA(entityManager -> {
                Post post = entityManager.find(Post.class, 1L);
                post.setTitle("High-Performance Hibernate");
            });
        } catch (Exception e) {
            LOGGER.error("Expected", e);
        }
    }

    @Test
    public void testEntityDelete() {
        LOGGER.info("Cache entries can be deleted");

        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);
            assertEquals(2, post.getComments().size());
        });

        printCacheRegionStatistics(Post.class.getName());
        printCollectionCacheRegionStatistics(Post.class, "comments");
        printCacheRegionStatistics(PostComment.class.getName());

        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);
            entityManager.remove(post);
        });

        printCacheRegionStatistics(Post.class.getName());
        printCacheRegionStatistics(PostComment.class.getName());
        printCollectionCacheRegionStatistics(Post.class, "comments");

        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);
            assertNull(post);
        });
    }

    @Entity(name = "Post_ReadOnly")
    @Table(name = "post_readonly")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Post {

        @Id
        private Long id;

        private String title;

        @OneToMany(mappedBy = "post",
                cascade = CascadeType.ALL, orphanRemoval = true)
        @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
        @Builder.Default
        private List<PostComment> comments = new ArrayList<>();

        public void addComment(PostComment comment) {
            comments.add(comment);
            comment.setPost(this);
        }
    }

    @Entity(name = "PostComment_ReadOnly")
    @Table(name = "post_comment_readonly")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PostComment {

        @Id
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        private Post post;

        private String review;
    }
}