package com.gringotts.hibernatecache.cacheentry.readonly;

import com.gringotts.hibernatecache.AbstractTestConfiguration;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;
import org.junit.Test;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ReadOnlyCacheConcurrencyStrategyImmutable extends AbstractTestConfiguration {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Post.class,
                PostComment.class
        };
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put("hibernate.cache.use_second_level_cache", Boolean.TRUE.toString());
        properties.put("hibernate.cache.region.factory_class", "jcache");
    }

    @Test
    public void testReadOnlyEntityUpdate() {
        doInJPA(entityManager -> {
            entityManager.persist(
                    new Post()
                            .setId(1L)
                            .setTitle("Welcome to Hibernate Caching")
                            .addComment(
                                    new PostComment()
                                            .setId(1L)
                                            .setReview("JDBC part review")
                            )
                            .addComment(
                                    new PostComment()
                                            .setId(2L)
                                            .setReview("Hibernate part review")
                            )
            );
        });
        printEntityCacheRegionStatistics(Post.class);
        printEntityCacheRegionStatistics(PostComment.class);
        printCollectionCacheRegionStatistics(Post.class, "comments");

        LOGGER.info("Post entity inserted");

        LOGGER.info("Read-only cache entries cannot be updated");
        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);
            post.setTitle("Welcome to Hibernate");
        });

        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);
            LOGGER.info("This is what the post title is " + post.title);
        });

        // clear cache and see what happens
        doInJPA(entityManager -> {
//            entityManager.getEntityManagerFactory().getCache().evictAll();
            Post post = entityManager.find(Post.class, 1L);
            LOGGER.info("This is what the post title is " + post.title);
        });

    }

    @Test
    public void testCollectionCacheUpdate() {
        doInJPA(entityManager -> {
            entityManager.persist(
                    new Post()
                            .setId(1L)
                            .setTitle("High-Performance Java Persistence")
                            .addComment(
                                    new PostComment()
                                            .setId(1L)
                                            .setReview("JDBC part review")
                            )
                            .addComment(
                                    new PostComment()
                                            .setId(2L)
                                            .setReview("Hibernate part review")
                            )
            );
        });
        printEntityCacheRegionStatistics(Post.class);
        printEntityCacheRegionStatistics(PostComment.class);
        printCollectionCacheRegionStatistics(Post.class, "comments");

        LOGGER.info("Post entity inserted");

        LOGGER.info("Read-only collection cache entries cannot be updated");

        try {
            doInJPA(entityManager -> {
                Post post = entityManager.find(Post.class, 1L);
                PostComment comment = post.getComments().remove(0);
                comment.setPost(null);
            });
        } catch (Exception e) {
            LOGGER.error("Expected", e);
        }
    }

    @Entity(name = "Post_ReadOnly_Immutable")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    @Immutable
    public static class Post {

        @Id
        private Long id;

        private String title;

        @Version
        private short version;

        @OneToMany(cascade = CascadeType.PERSIST, mappedBy = "post")
        @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
        @Immutable
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

    @Entity(name = "PostComment_ReadOnly_Immutable")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    @Immutable
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
