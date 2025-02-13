package org.jeecg.modules.KM.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {


    @Bean
    ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        return builder.defaultSystem("  你是一个友好的知识库聊天机器人，用中文回答问题")
                .defaultAdvisors(
                        new QuestionAnswerAdvisor(vectorStore), // RAG
                        new SimpleLoggerAdvisor()
                )
                .build();
    }
}
