package com.gringotts.hibernatecache.cacheentry;

import com.gringotts.hibernatecache.AbstractTestConfiguration;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.junit.Test;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OneToManyRelationshipEntryView extends AbstractTestConfiguration {

    @Override
    protected Class<?>[] entities() {
        return new Class[]{Post.class,
                PostComment.class};
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
            post.addComment(new PostComment().setId(1L).setReview("This is one comment"));
            post.addComment(new PostComment().setId(2L).setReview("This is two comment"));
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
        // Count how many hits were on post comments + explanation + solution
        // Count how many hits were on post's comments when cached + explanation + solution
    }

    @Entity(name = "Post")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    public static class Post {

        @Id
        private Long id;

        private String title;

        @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
        @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
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

    @Entity(name = "PostComment")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    public static class PostComment {

        @Id
        private Long id;

        @ManyToOne
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
