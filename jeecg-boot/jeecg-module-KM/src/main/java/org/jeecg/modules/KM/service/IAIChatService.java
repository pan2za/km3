package org.jeecg.modules.KM.service;

import org.springframework.ai.chat.client.ChatClient;

public interface IAIChatService {

    String ragchat(String message);

    ChatClient.ChatClientRequestSpec simpleChat(String userMessage);

    ChatClient.ChatClientRequestSpec chatByVectorStore(String message);
}
