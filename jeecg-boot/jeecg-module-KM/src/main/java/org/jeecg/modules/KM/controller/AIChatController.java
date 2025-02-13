package org.jeecg.modules.KM.controller;

import org.jeecg.modules.KM.service.IAIChatService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/KM/chat")
public class AIChatController {

    private final IAIChatService chatService;

    public AIChatController(IAIChatService chatService) {
        this.chatService = chatService;
    }

    public record ChatRequest(String message) {
    }

    /**
     * 流式对话
     *
     * @param request 用户指令
     * @return
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest request) {
        return chatService.chatByVectorStore(request.message)
                .stream()
                .content().map(content -> ServerSentEvent.builder(content).build())
                .concatWithValues(ServerSentEvent.builder("[DONE]").build())
                .onErrorResume(e -> Flux.just(ServerSentEvent.builder("[ERROR]" + e.getMessage()).event("error").build()));
    }

}
