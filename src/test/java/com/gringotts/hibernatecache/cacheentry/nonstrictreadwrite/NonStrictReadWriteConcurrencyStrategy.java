package com.gringotts.hibernatecache.cacheentry.nonstrictreadwrite;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NonStrictReadWriteConcurrencyStrategy extends AbstractTestConfiguration {

    @Override
    protected Class<?>[] entities() {
        return new Class[] {Post.class, PostComment.class};
    }

    public void afterInit() {
        doInJPA(entityManager -> {
            final var post = Post.builder().id(1L).title("Hibernate Caching").build();
            post.addComment(PostComment.builder().id(1L).review("This is easy").build());
            post.addComment(PostComment.builder().id(2L).review("This is hard").build());
            entityManager.persist(post);
        });
        printEntityCacheRegionStatistics(Post.class);
        printEntityCacheRegionStatistics(PostComment.class);
        printCollectionCacheRegionStatistics(Post.class, "comments");

        LOGGER.info("Post entity inserted");
    }


    @Test
    public void postEntityLoad() {

        LOGGER.info("Load Post entity and comments collection");

        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);
            printEntityCacheRegionStatistics(Post.class);
            assertEquals(2, post.getComments().size());
            printCollectionCacheRegionStatistics(Post.class, "comments");
        });
    }

    @Test
    public void postEntityEvictModifyLoad() {

        LOGGER.info("Evict, modify, load");

        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);
            entityManager.detach(post);

            post.setTitle("Welcome to Hibernate");
            entityManager.merge(post);
            entityManager.flush();

            entityManager.detach(post);
            post = entityManager.find(Post.class, 1L);
            printEntityCacheRegionStatistics(Post.class);
        });
    }

    @Test
    public void postEntityUpdate() {
        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);
            assertEquals(2, post.getComments().size());
        });

        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);
            post.setTitle("Welcome to Hibernate");
        });

        printCacheRegionStatistics(Post.class.getName());
    }

    @Test
    public void postCommentsCollectionUpdate() {
        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);
            assertEquals(2, post.getComments().size());
        });

        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);

            PostComment comment = post.getComments().remove(0);
            comment.setPost(null);
        });

        printCollectionCacheRegionStatistics(Post.class, "comments");
        printCacheRegionStatistics(PostComment.class.getName());
    }

    @Test
    public void nonVersionedEntityUpdate() {
        doInJPA(entityManager -> {
            PostComment comment = entityManager.find(PostComment.class, 1L);
        });
        printCacheRegionStatistics(PostComment.class.getName());
        doInJPA(entityManager -> {
            PostComment comment = entityManager.find(PostComment.class, 1L);
            comment.setReview("This is both hard and easy");
        });
        printCacheRegionStatistics(PostComment.class.getName());
    }

    @Test
    public void postEntityDelete() {
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
        printCollectionCacheRegionStatistics(Post.class, "comments");
        printCacheRegionStatistics(PostComment.class.getName());

        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);
            assertNull(post);
        });
    }

    @Entity(name = "Post_NonStrictReadWrite")
    @Table(name = "post_nonstrictreadwrite")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Post {

        @Id
        private Long id;

        private String title;

        @OneToMany(cascade = CascadeType.ALL, mappedBy = "post", orphanRemoval = true)
        @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
        @Builder.Default
        private List<PostComment> comments = new ArrayList<>();

        public void addComment(PostComment comment) {
            comments.add(comment);
            comment.setPost(this);
        }
    }

    @Entity(name = "PostComment_NonStrictReadWrite")
    @Table(name = "post_comment_nonstrictreadwrite")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
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
