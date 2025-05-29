package com.example.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.backend.config.TestConfig;
import com.example.backend.config.TestJwtConfig;
import com.example.backend.config.TestSecurityConfig;
import com.example.backend.model.dto.ChatDto;
import com.example.backend.model.dto.MessageDto;
import com.example.backend.repository.ChatRepository;
import com.example.backend.repository.MessageRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.ChatService;
import com.example.backend.service.DatabaseConnectionService;

@WebMvcTest(ChatController.class)
@Import({TestConfig.class, TestSecurityConfig.class, TestJwtConfig.class})
@ActiveProfiles("test")
public class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private ChatRepository chatRepository;

    @MockBean
    private MessageRepository messageRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DatabaseConnectionService databaseConnectionService;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @WithMockUser(username = "testuser")
    public void getUserChats_ReturnsOk() throws Exception {
        List<ChatDto> chats = Arrays.asList(
            ChatDto.builder().id(1L).title("Chat 1").build(),
            ChatDto.builder().id(2L).title("Chat 2").build()
        );

        when(chatService.getUserChats(any(Long.class))).thenReturn(chats);

        mockMvc.perform(get("/chats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Chat 1"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].title").value("Chat 2"));
    }

    @Test
    @WithMockUser(username = "testuser")
    public void createChat_ValidRequest_ReturnsOk() throws Exception {
        ChatDto chatDto = ChatDto.builder()
                .id(1L)
                .title("New Chat")
                .build();

        when(chatService.createChat(any(Long.class), any(ChatDto.class))).thenReturn(chatDto);

        mockMvc.perform(post("/chats")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"New Chat\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("New Chat"));
    }

    @Test
    @WithMockUser(username = "testuser")
    public void getChat_ValidId_ReturnsOk() throws Exception {
        ChatDto chatDto = ChatDto.builder()
                .id(1L)
                .title("Test Chat")
                .build();

        when(chatService.getChat(eq(1L), any(Long.class))).thenReturn(chatDto);

        mockMvc.perform(get("/chats/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Test Chat"));
    }

    @Test
    @WithMockUser(username = "testuser")
    public void updateChat_ValidRequest_ReturnsOk() throws Exception {
        ChatDto chatDto = ChatDto.builder()
                .id(1L)
                .title("Updated Chat")
                .build();

        when(chatService.updateChat(eq(1L), any(Long.class), any(ChatDto.class))).thenReturn(chatDto);

        mockMvc.perform(post("/chats/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated Chat\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Updated Chat"));
    }

    @Test
    @WithMockUser(username = "testuser")
    public void deleteChat_ValidId_ReturnsOk() throws Exception {
        mockMvc.perform(delete("/chats/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "testuser")
    public void archiveChat_ValidId_ReturnsNoContent() throws Exception {
        mockMvc.perform(post("/chats/1/archive"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "testuser")
    public void getChatMessages_ValidId_ReturnsOk() throws Exception {
        List<MessageDto> messages = Arrays.asList(
            MessageDto.builder().id(1L).content("Message 1").build(),
            MessageDto.builder().id(2L).content("Message 2").build()
        );

        when(chatService.getChatMessages(eq(1L), any(Long.class))).thenReturn(messages);

        mockMvc.perform(get("/chats/1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].content").value("Message 1"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].content").value("Message 2"));
    }

    @Test
    @WithMockUser(username = "testuser")
    public void sendMessage_ValidRequest_ReturnsOk() throws Exception {
        MessageDto messageDto = MessageDto.builder()
                .id(1L)
                .content("New Message")
                .build();

        when(chatService.sendMessage(eq(1L), any(Long.class), any(MessageDto.class))).thenReturn(messageDto);

        mockMvc.perform(post("/chats/1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"New Message\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("New Message"));
    }
} 