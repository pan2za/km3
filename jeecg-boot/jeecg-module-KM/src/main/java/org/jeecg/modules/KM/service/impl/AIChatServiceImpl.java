package org.jeecg.modules.KM.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.KM.service.IAIChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AIChatServiceImpl implements IAIChatService {
    private final ChatClient chatClient;

    private final VectorStore vectorStore;


    // 系统提示词
//    private final static String SYSTEM_PROMPT = """
//            你需要使用文档内容对用户提出的问题进行回复，同时你需要表现得天生就知道这些内容，
//            不能在回复中体现出你是根据给出的文档内容进行回复的，这点非常重要。
//            当用户提出的问题无法根据文档内容进行回复或者你也不清楚时，回复不知道即可。
//
//            文档内容如下:
//            {documents}
//
//            """;
    private final static String SYSTEM_PROMPT = """
            仅根据以下内容回复

            文档内容如下:
            {documents}
            """;


//    private final static String SYSTEM_PROMPT = """
//            下面是上下文信息
//            ---------------------
//            {question_answer_context}
//            ---------------------
//            给定的上下文和提供的历史信息，而不是事先的知识，回复用户的意见。如果答案不在上下文中，告诉用户你不能回答这个问题。
//            """;

    @Autowired
    public AIChatServiceImpl(VectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    // 简单的对话，不对向量数据库进行检索
    public ChatClient.ChatClientRequestSpec simpleChat(String userMessage) {
        return chatClient.prompt().user(userMessage);
    }


    // 通过向量数据库进行检索
    public ChatClient.ChatClientRequestSpec chatByVectorStore(String message) {
//        // 根据问题文本进行相似性搜索
        List<Document> listOfSimilarDocuments = vectorStore.similaritySearch(SearchRequest.builder().query(message).topK(5).build());
        // 将Document列表中每个元素的content内容进行拼接获得documents
        String documents = null;
        if (listOfSimilarDocuments != null) {
            documents = listOfSimilarDocuments.stream().map(Document::getText).peek(e -> log.debug("{}", e))
                    .collect(Collectors.joining());
            log.info(documents);
        }
        // 使用Spring AI 提供的模板方式构建SystemMessage对象
        Message systemMessage = null;
        if (documents != null) {
            systemMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(
                    Map.of("documents", documents));
        }
        // 构建UserMessage对象
        UserMessage userMessage = new UserMessage(message);
//         将Message列表一并发送给ChatGPT

        return chatClient.prompt(new Prompt(List.of(systemMessage, userMessage)));
//        return chatClient.prompt(message).advisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.builder().query(message).build(), SYSTEM_PROMPT));
    }


    /**
     * 问答,根据输入内容回答
     *
     * @param message 输入内容
     * @return 回答内容
     */
    public String ragchat(String message) {
        //查询获取文档信息
        if (message != null && !message.isEmpty()) {
            log.info("Chat with VectorStore and ollama: {}", message);
            List<Document> listOfSimilarDocuments = vectorSearch(message);

            String documents = null;
            if (documents != null) {
                documents = listOfSimilarDocuments.stream().map(Document::getText)
                        .collect(Collectors.joining());
            }
            // 使用Spring AI 提供的模板方式构建SystemMessage对象
            Message systemMessage = null;
            if (documents != null) {
                systemMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(
                        Map.of("documents", documents));
            }
            // 构建UserMessage对象
            UserMessage userMessage = new UserMessage(message);
            // 将Message列表一并发送给ChatGPT
            String out = chatClient.prompt(new Prompt(List.of(systemMessage, userMessage))).call().content();
            log.info("Chat response: {}", out);
            return out;
        } else {
            return null;
        }
    }


    /**
     * 根据关键词搜索向量库
     *
     * @param keyword 关键词
     * @return 文档列表
     */
    public List<Document> vectorSearch(String keyword) {
        if (keyword != null) {
            List<Document> docs = vectorStore.similaritySearch(keyword);
            if (docs != null && !docs.isEmpty()) {
                //FIXME: NOT KNOWN THE REASON WHY I SHOULD MERGE.
                //return mergeDocuments(docs);

                for (Document document : docs) {
                    // 这里可以对 document 进行操作，例如打印其信息
                    log.info("the who vectorsearched document: {}", document.getText());
                }
                return docs;
            } else {
                log.warn("similaritySearch : none of the {} found", keyword);
                return null;
            }
        } else {
            log.warn("similaritySearch: keyword is none, should not be none.");
            return null;
        }
    }


//    public VectorStore vectorStore() {
//        JedisPooled jedis = jedisPooled();
//        VectorStore vs = RedisVectorStore.builder(jedis, embeddingModel())
//                .indexName("custom-index")                // Optional: defaults to "spring-ai-index"
//                .prefix("custom-prefix:")                  // Optional: defaults to "embedding:"
//                //       .metadataFields(                         // Optional: define metadata fields for filtering
//                //           MetadataField.tag("country"),
//                //           MetadataField.numeric("year"))
//                .initializeSchema(true)                   // Optional: defaults to false
//
//                .batchingStrategy(new TokenCountBatchingStrategy()) // Optional: defaults to TokenCountBatchingStrategy
//                .build();
//        /***/
//        /** fix spring-ai issue: not create indexName */
//        // If index already exists don't do anything
//        if (jedis.ftList().contains("custom-index")) {
//            return vs;
//        } else {
//            String response = jedis.ftCreate("custom-index",
//                    FTCreateParams.createParams().on(IndexDataType.JSON).addPrefix("custom-prefix:"), schemaFields());
//            log.info("create custom-index vector store, result " + response);
//        }
//        return vs;
//    }

    // This can be any EmbeddingModel implementation
//    public EmbeddingModel embeddingModel() {
//        OllamaApi ollamaApi = new OllamaApi("http://jeecg-boot-ollama-1:11434");
//        OllamaOptions option = OllamaOptions.builder().model("mofanke/dmeta-embedding-zh").build();
//        return OllamaEmbeddingModel.builder()
//                .ollamaApi(ollamaApi)
//                .defaultOptions(option)
//                .modelManagementOptions(ModelManagementOptions.builder()
//                        .pullModelStrategy(PullModelStrategy.WHEN_MISSING)
//                        .build())
//                .build();
//    }


    private Iterable<SchemaField> schemaFields() {
        Map<String, Object> vectorAttrs = new HashMap<>();
        vectorAttrs.put("DIM", 768);/*FIXME: if you change the embedding model above, you MUST change to the corresponding dimension. bge_large_zh -> 1024, some -> 768*/
        vectorAttrs.put("DISTANCE_METRIC", "COSINE");
        vectorAttrs.put("TYPE", "FLOAT32");
        List<SchemaField> fields = new ArrayList<>();
        fields.add(TextField.of("$.content").as("content").weight(1.0));
        fields.add(VectorField.builder()
                .fieldName("$.embedding")
                .algorithm(VectorField.VectorAlgorithm.HNSW)
                .attributes(vectorAttrs)
                .as("embedding")
                .build());

        return fields;
    }

}
