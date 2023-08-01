package com.gringotts.hibernatecache.cacheentry;

import com.gringotts.hibernatecache.AbstractTestConfiguration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
            post.addComment(PostComment.builder().id(1L).review("This is one comment").build());
            post.addComment(PostComment.builder().id(2L).review("This is two comment").build());
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
    @Data
    public static class Post {

        @Id
        private Long id;

        private String title;

        @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<PostComment> comments = new ArrayList<>();

        public void addComment(PostComment comment) {
            comments.add(comment);
            comment.setPost(this);
        }
    }

    @Entity(name = "PostComment")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostComment {

        @Id
        private Long id;

        @ManyToOne
        private Post post;

        private String review;
    }
}
