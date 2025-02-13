package org.jeecg.modules.KM.common.config;


import io.micrometer.observation.ObservationRegistry;
import lombok.AllArgsConstructor;
import org.springframework.ai.autoconfigure.vectorstore.redis.RedisVectorStoreAutoConfiguration;
import org.springframework.ai.autoconfigure.vectorstore.redis.RedisVectorStoreProperties;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

@Configuration
// 禁用SpringAI提供的RedisStack向量数据库的自动配置，会和Redis的配置冲突。
@EnableAutoConfiguration(exclude = {RedisVectorStoreAutoConfiguration.class})
// 读取RedisStack的配置信息
@EnableConfigurationProperties({RedisVectorStoreProperties.class})
@AllArgsConstructor
public class RedisVectorConfig {


    @Bean
    BatchingStrategy batchingStrategy() {
        return new TokenCountBatchingStrategy();
    }

    /**
     * 创建RedisStack向量数据库
     *
     * @param embeddingModel 嵌入模型
     * @param properties     redis-stack的配置信息
     * @return vectorStore 向量数据库
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel, RedisVectorStoreProperties properties,
                                   RedisProperties redisProperties,
                                   ObjectProvider<ObservationRegistry> observationRegistry,
                                   ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
                                   BatchingStrategy batchingStrategy) {
//        return RedisVectorStore.builder(
//                new JedisPooled(redisProperties.getHost(),
//                        redisProperties.getPort()),
//                embeddingModel
//        ).build();
        return ((RedisVectorStore.Builder) ((RedisVectorStore.Builder) RedisVectorStore.builder(new JedisPooled(redisProperties.getHost(), redisProperties.getPort()),
                        embeddingModel).initializeSchema(properties.isInitializeSchema())
                .observationRegistry((ObservationRegistry) observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP)))
                .customObservationConvention((VectorStoreObservationConvention) customObservationConvention.getIfAvailable(() -> null)))
                .batchingStrategy(batchingStrategy).indexName(properties.getIndex()).prefix(properties.getPrefix()).build();
    }
}