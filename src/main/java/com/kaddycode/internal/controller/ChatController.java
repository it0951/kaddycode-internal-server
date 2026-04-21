package com.kaddycode.internal.controller;

import com.kaddycode.internal.domain.entity.ChatHistory;
import com.kaddycode.internal.dto.request.ChatRequest;
import com.kaddycode.internal.dto.response.ChatResponse;
import com.kaddycode.internal.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/completions")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.chat(request));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ChatHistory>> getHistory(@RequestParam String userId) {
        return ResponseEntity.ok(chatService.getHistory(userId));
    }
}