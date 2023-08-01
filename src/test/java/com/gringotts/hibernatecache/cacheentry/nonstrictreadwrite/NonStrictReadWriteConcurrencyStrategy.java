package com.gringotts.hibernatecache.cacheentry.nonstrictreadwrite;

import com.gringotts.hibernatecache.AbstractTestConfiguration;
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
            entityManager.persist(
                    new Post()
                            .setId(1L)
                            .setTitle("Welcome to Hibernate Caching")
                            .addComment(
                                    new PostComment()
                                            .setId(1L)
                                            .setReview("This is hard")
                            )
                            .addComment(
                                    new PostComment()
                                            .setId(2L)
                                            .setReview("This is easy")
                            )
            );
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
    public static class Post {

        @Id
        private Long id;

        private String title;

        @OneToMany(cascade = CascadeType.ALL, mappedBy = "post", orphanRemoval = true)
        @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
        private List<PostComment> comments = new ArrayList<>();

        public Long getId() {
            return id;
        }

        public Post setId(Long id) {
            this.id = id;
            return this;
        }

        public String getTitle() {
            return title;
        }

        public Post setTitle(String title) {
            this.title = title;
            return this;
        }

        public List<PostComment> getComments() {
            return comments;
        }

        public Post addComment(PostComment comment) {
            comments.add(comment);
            comment.setPost(this);
            return this;
        }
    }

    @Entity(name = "PostComment_NonStrictReadWrite")
    @Table(name = "post_comment_nonstrictreadwrite")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    public static class PostComment {

        @Id
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        private Post post;

        private String review;

        public Long getId() {
            return id;
        }

        public PostComment setId(Long id) {
            this.id = id;
            return this;
        }

        public Post getPost() {
            return post;
        }

        public PostComment setPost(Post post) {
            this.post = post;
            return this;
        }

        public String getReview() {
            return review;
        }

        public PostComment setReview(String review) {
            this.review = review;
            return this;
        }
    }
}
