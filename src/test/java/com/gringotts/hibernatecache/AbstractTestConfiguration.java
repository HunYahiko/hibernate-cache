package com.gringotts.hibernatecache;


import com.gringotts.hibernatecache.domain.Post;
import com.gringotts.hibernatecache.domain.PostComment;
import com.gringotts.hibernatecache.domain.PostDetail;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.ehcache.core.Ehcache;
import org.hibernate.Interceptor;
import org.hibernate.SessionFactory;
import org.hibernate.cache.internal.QueryResultsCacheImpl;
import org.hibernate.cache.jcache.internal.JCacheAccessImpl;
import org.hibernate.cache.spi.entry.CollectionCacheEntry;
import org.hibernate.cache.spi.entry.StandardCacheEntryImpl;
import org.hibernate.cache.spi.support.AbstractReadWriteAccess;
import org.hibernate.cache.spi.support.AbstractRegion;
import org.hibernate.cache.spi.support.DirectAccessRegionTemplate;
import org.hibernate.cache.spi.support.DomainDataRegionTemplate;
import org.hibernate.cache.spi.support.StorageAccess;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.internal.PersistenceUnitInfoDescriptor;
import org.hibernate.stat.CacheRegionStatistics;
import org.hibernate.stat.Statistics;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@RunWith(SpringRunner.class)
@SpringBootTest
@EnableConfigurationProperties
public abstract class AbstractTestConfiguration {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    static {
        Thread.currentThread().setName("Current-Thread");
    }

    protected final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread bob = new Thread(r);
        bob.setName("Second-Thread");
        return bob;
    });

    protected EntityManagerFactory entityManagerFactory;

    @Autowired
    private DataSource dataSource;

    @ClassRule
    public static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:15.3")
            .withDatabaseName("hibernate_cache")
            .withUsername("sa")
            .withPassword("sa");

    @Before
    public void setUp() {
        entityManagerFactory = newEntityManagerFactory();
        afterInit();
    }

    protected void afterInit() {

    }

    protected EntityManagerFactory newEntityManagerFactory() {
        PersistenceUnitInfo persistenceUnitInfo = persistenceUnitInfo(getClass().getSimpleName());
        Map configuration = properties();
        Interceptor interceptor = interceptor();
        if (interceptor != null) {
            configuration.put(AvailableSettings.INTERCEPTOR, interceptor);
        }

        EntityManagerFactoryBuilderImpl entityManagerFactoryBuilder = new EntityManagerFactoryBuilderImpl(
                new PersistenceUnitInfoDescriptor(persistenceUnitInfo), configuration
        );
        return entityManagerFactoryBuilder.build();
    }

    protected Interceptor interceptor() {
        return null;
    }

    protected PersistenceUnitInfoImpl persistenceUnitInfo(String name) {
        return new PersistenceUnitInfoImpl(
                name, entityClassNames(), properties()
        );
    }

    protected Class<?>[] entities() {
        return new Class[]{Post.class, PostComment.class, PostDetail.class};
    }

    protected List<String> entityClassNames() {
        return Arrays.stream(entities()).map(Class::getName).collect(Collectors.toList());
    }

    protected Properties properties() {
        Properties properties = new Properties();
        //log settings
        properties.put("hibernate.hbm2ddl.auto", "create");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQL82Dialect");
        //data source settings
        if (dataSource != null) {
            properties.put("hibernate.connection.datasource", dataSource);
        }
        properties.put("hibernate.generate_statistics", Boolean.TRUE.toString());
        properties.put("hibernate.cache.region.factory_class", "jcache");

        properties.put("net.sf.ehcache.configurationResourceName",
                       Thread.currentThread().getContextClassLoader().getResource("ehcache.xml").toString());

        additionalProperties(properties);
        return properties;
    }

    protected void additionalProperties(Properties properties) {

    }

    protected void printQueryCacheRegionStatistics() {
        printCacheRegionStatisticsEntries("default-query-results-region");
        printCacheRegionStatisticsKeys("default-query-results-region");
    }

    protected void printEntityCacheRegionStatistics(Class<?> entityClass) {
        printCacheRegionStatisticsEntries(entityClass.getName());
    }

    protected void printCacheRegionStatistics(String region) {
        printCacheRegionStatisticsEntries(region);
        printCacheRegionStatisticsKeys(region);
    }

    protected void printCollectionCacheRegionStatistics(Class<?> entityClass, String collection) {
        printCacheRegionStatisticsEntries(entityClass.getName() + "." + collection);
        printCacheRegionStatisticsKeys(entityClass.getName() + "." + collection);
    }

    private void printCacheRegionStatisticsEntries(String regionName) {
        SessionFactory sessionFactory = sessionFactory();
        Statistics statistics = sessionFactory.getStatistics();
        if (sessionFactory.getSessionFactoryOptions().isQueryCacheEnabled()) {
            ReflectionUtils.invokeMethod(statistics, "getQueryRegionStats", "default-query-results-region");
        }

        CacheRegionStatistics cacheRegionStatistics = "default-query-results-region".equals(regionName) ?
                statistics.getQueryRegionStatistics(regionName) :
                statistics.getDomainDataRegionStatistics(regionName);

        if (cacheRegionStatistics != null) {
            AbstractRegion region = ReflectionUtils.getFieldValue(cacheRegionStatistics, "region");

            StorageAccess storageAccess = getStorageAccess(region);
            Ehcache cache = getEhcache(storageAccess);

            if (cache != null) {
                StringBuilder cacheEntriesBuilder = new StringBuilder();
                cacheEntriesBuilder.append("[");

                boolean firstEntry = true;

                Object onHeapStore = ReflectionUtils.getFieldValue(cache, "store");
                Object onHeapStoreMap = ReflectionUtils.getFieldValue(onHeapStore, "map");
                Iterable keySet = ReflectionUtils.invokeMethod(onHeapStoreMap, "keySet");
                for (Object key : keySet) {
                    Object cacheValue = storageAccess.getFromCache(key, null);

                    if (!firstEntry) {
                        cacheEntriesBuilder.append(",\n");
                    } else {
                        cacheEntriesBuilder.append("\n");
                        firstEntry = false;
                    }
                    cacheEntriesBuilder.append("\t");

                    if (cacheValue instanceof QueryResultsCacheImpl.CacheItem) {
                        QueryResultsCacheImpl.CacheItem queryValue = (QueryResultsCacheImpl.CacheItem) cacheValue;

                        cacheEntriesBuilder.append(
                                ToStringBuilder.reflectionToString(queryValue, ToStringStyle.SHORT_PREFIX_STYLE)
                        );
                    } else if (cacheValue instanceof StandardCacheEntryImpl) {
                        StandardCacheEntryImpl standardCacheEntry = (StandardCacheEntryImpl) cacheValue;

                        cacheEntriesBuilder.append(
                                ToStringBuilder.reflectionToString(standardCacheEntry, ToStringStyle.SHORT_PREFIX_STYLE)
                        );
                    } else if (cacheValue instanceof CollectionCacheEntry) {
                        CollectionCacheEntry collectionCacheEntry = (CollectionCacheEntry) cacheValue;

                        cacheEntriesBuilder.append(
                                ToStringBuilder.reflectionToString(collectionCacheEntry,
                                                                   ToStringStyle.SHORT_PREFIX_STYLE)
                        );
                    } else if (cacheValue instanceof AbstractReadWriteAccess.Item) {
                        AbstractReadWriteAccess.Item valueItem = (AbstractReadWriteAccess.Item) cacheValue;
                        Object value = valueItem.getValue();

                        if (value instanceof StandardCacheEntryImpl) {
                            StandardCacheEntryImpl standardCacheEntry = ((StandardCacheEntryImpl) value);
                            cacheEntriesBuilder.append(
                                    ToStringBuilder.reflectionToString(standardCacheEntry,
                                                                       ToStringStyle.SHORT_PREFIX_STYLE)
                            );
                        } else if (value.getClass().getPackageName().startsWith("java")) {
                            cacheEntriesBuilder.append(value);
                        } else {
                            cacheEntriesBuilder.append(
                                    ToStringBuilder.reflectionToString(valueItem.getValue(),
                                                                       ToStringStyle.SHORT_PREFIX_STYLE)
                            );
                        }
                    } else if (cacheValue instanceof AbstractReadWriteAccess.Lockable) {
                        cacheEntriesBuilder.append(
                                ToStringBuilder.reflectionToString(cacheValue, ToStringStyle.SHORT_PREFIX_STYLE)
                        );
                    }
                }

                cacheEntriesBuilder.append("\n]");

                LOGGER.debug(
                        "\nRegion: {},\nStatistics: {},\nEntries: {}",
                        regionName,
                        cacheRegionStatistics,
                        cacheEntriesBuilder
                );
            }
        }
    }

    private void printCacheRegionStatisticsKeys(String regionName) {
        SessionFactory sessionFactory = sessionFactory();
        Statistics statistics = sessionFactory.getStatistics();
        if (sessionFactory.getSessionFactoryOptions().isQueryCacheEnabled()) {
            ReflectionUtils.invokeMethod(statistics, "getQueryRegionStats", "default-query-results-region");
        }

        CacheRegionStatistics cacheRegionStatistics = "default-query-results-region".equals(regionName) ?
                statistics.getQueryRegionStatistics(regionName) :
                statistics.getDomainDataRegionStatistics(regionName);

        if (cacheRegionStatistics != null) {
            AbstractRegion region = ReflectionUtils.getFieldValue(cacheRegionStatistics, "region");

            StorageAccess storageAccess = getStorageAccess(region);
            Ehcache cache = getEhcache(storageAccess);

            if (cache != null) {
                StringBuilder cacheKeysBuilder = new StringBuilder();
                cacheKeysBuilder.append("[");

                boolean firstEntry = true;

                Object onHeapStore = ReflectionUtils.getFieldValue(cache, "store");
                Object onHeapStoreMap = ReflectionUtils.getFieldValue(onHeapStore, "map");
                Iterable keySet = ReflectionUtils.invokeMethod(onHeapStoreMap, "keySet");
                for (Object key : keySet) {

                    if (!firstEntry) {
                        cacheKeysBuilder.append(",\n");
                    } else {
                        cacheKeysBuilder.append("\n");
                        firstEntry = false;
                    }
                    cacheKeysBuilder.append("\t");

                    cacheKeysBuilder.append(
                            ToStringBuilder.reflectionToString(key, ToStringStyle.SHORT_PREFIX_STYLE)
                    );

                }

                cacheKeysBuilder.append("\n]");

                LOGGER.debug(
                        "\nRegion: {},\nStatistics: {},\nKeys: {}",
                        regionName,
                        cacheRegionStatistics,
                        cacheKeysBuilder
                );
            }
        }
    }

    protected SessionFactory sessionFactory() {
        EntityManagerFactory entityManagerFactory = entityManagerFactory();
        if (entityManagerFactory == null) {
            return null;
        }
        return entityManagerFactory.unwrap(SessionFactory.class);
    }

    protected EntityManagerFactory entityManagerFactory() {
        return entityManagerFactory;
    }

    protected StorageAccess getStorageAccess(AbstractRegion region) {
        if (region instanceof DirectAccessRegionTemplate) {
            DirectAccessRegionTemplate directAccessRegionTemplate = (DirectAccessRegionTemplate) region;
            return directAccessRegionTemplate.getStorageAccess();
        } else if (region instanceof DomainDataRegionTemplate) {
            DomainDataRegionTemplate domainDataRegionTemplate = (DomainDataRegionTemplate) region;
            return domainDataRegionTemplate.getCacheStorageAccess();
        }
        throw new IllegalArgumentException("Unsupported region: " + region);
    }

    protected Ehcache getEhcache(StorageAccess storageAccess) {
        Object cacheHolder = storageAccess;
        if (storageAccess instanceof JCacheAccessImpl) {
            cacheHolder = ReflectionUtils.getFieldValue(storageAccess, "underlyingCache");
        }
        return ReflectionUtils.getFieldValue(cacheHolder, "ehCache");
    }

    protected void doInJPA(Consumer<EntityManager> function) {
        EntityManager entityManager = null;
        EntityTransaction txn = null;
        try {
            entityManager = entityManagerFactory.createEntityManager();
            txn = entityManager.getTransaction();
            txn.begin();
            function.accept(entityManager);
            if (!txn.getRollbackOnly()) {
                txn.commit();
            } else {
                try {
                    txn.rollback();
                } catch (Exception e) {
                    LOGGER.error("Rollback failure", e);
                }
            }
        } catch (Throwable t) {
            if (txn != null && txn.isActive()) {
                try {
                    txn.rollback();
                } catch (Exception e) {
                    LOGGER.error("Rollback failure", e);
                }
            }
            throw t;
        } finally {
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

    protected void executeSync(VoidCallable callable) {
        executeSync(Collections.singleton(callable));
    }

    protected <T> T executeSync(Callable<T> callable) {
        try {
            return executorService.submit(callable).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    protected void executeSync(Collection<VoidCallable> callables) {
        try {
            List<Future<Void>> futures = executorService.invokeAll(callables);
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    protected void executeAsync(Runnable callable, final Runnable completionCallback) {
        final Future future = executorService.submit(callable);
        new Thread(() -> {
            while (!future.isDone()) {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            try {
                completionCallback.run();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).start();
    }

    protected Future<?> executeAsync(Runnable callable) {
        return executorService.submit(callable);
    }
}
