package com.gringotts.hibernatecache.cacheentry;

import com.gringotts.hibernatecache.AbstractTestConfiguration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;
import org.junit.Test;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Properties;

import static org.junit.Assert.assertNotNull;

public class SimpleCacheReferenceEntryAndKeyView extends AbstractTestConfiguration {

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put("hibernate.cache.use_reference_entries", Boolean.TRUE.toString());
    }

    @Override
    protected Class<?>[] entities() {
        return new Class[] {Post.class};
    }

    @Test
    public void simpleCacheReferenceView() {
        doInJPA(entityManager -> entityManager.persist(
                Post.builder()
                        .id(1L)
                        .title("Welcome to Hibernate Caching")
                        .build()
        ));

        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);
            assertNotNull(post);
        });

        printCacheRegionStatistics(Post.class.getName());

        /* Not working for some reason due to some changes,
          even if this information is provided in the book.
          Or we cannot already access this information
        */
    }

    @Entity(name = "Post_CacheEntry")
    @Immutable
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Post {

        @Id
        private Long id;

        private String title;
    }
}
