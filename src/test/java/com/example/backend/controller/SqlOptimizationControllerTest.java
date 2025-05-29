package com.example.backend.controller;

import com.example.backend.config.TestConfig;
import com.example.backend.config.TestJwtConfig;
import com.example.backend.config.TestSecurityConfig;
import com.example.backend.model.dto.MessageDto;
import com.example.backend.model.dto.SqlQueryRequest;
import com.example.backend.model.dto.SqlQueryResponse;
import com.example.backend.model.entity.User;
import com.example.backend.repository.*;
import com.example.backend.security.CustomUserDetails;
import com.example.backend.service.ChatService;
import com.example.backend.service.DatabaseConnectionService;
import com.example.backend.service.LLMService;
import com.example.backend.service.SqlOptimizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SqlOptimizationController.class)
@Import({TestConfig.class, TestSecurityConfig.class, TestJwtConfig.class})
@ActiveProfiles("test")
public class SqlOptimizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SqlOptimizationService sqlOptimizationService;

    @MockBean
    private SqlQueryRepository sqlQueryRepository;

    @MockBean
    private MessageRepository messageRepository;

    @MockBean
    private ChatRepository chatRepository;

    @MockBean
    private DatabaseConnectionRepository databaseConnectionRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private ChatService chatService;

    @MockBean
    private DatabaseConnectionService databaseConnectionService;

    @MockBean
    private LLMService llmService;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    private void setupSecurityContext() {
        // Создаем объект User для CustomUserDetails
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("password");

        // Создаем CustomUserDetails
        CustomUserDetails userDetails = new CustomUserDetails(user);

        // Настраиваем SecurityContextHolder
        SecurityContextHolder.setContext(
                new SecurityContextImpl(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        )
                )
        );
    }

    @BeforeEach
    public void setUp() {
        // Очищаем SecurityContext перед каждым тестом
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(username = "testuser")
    public void optimizeQuery_ValidRequest_ReturnsOk() throws Exception {
        // Устанавливаем CustomUserDetails перед запросом
        setupSecurityContext();

        MessageDto messageDto = MessageDto.builder()
                .id(1L)
                .content("Optimized query")
                .fromUser(false)
                .build();

        SqlQueryResponse response = SqlQueryResponse.builder()
                .id(1L)
                .originalQuery("SELECT * FROM users")
                .optimizedQuery("SELECT id, name, email FROM users")
                .executionTimeMs(100L)
                .createdAt(LocalDateTime.now())
                .message(messageDto)
                .build();

        when(sqlOptimizationService.optimizeQuery(any(Long.class), any(SqlQueryRequest.class)))
                .thenReturn(Mono.just(response));

        mockMvc.perform(post("/sql/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":1,\"query\":\"SELECT * FROM users\",\"databaseConnectionId\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.originalQuery").value("SELECT * FROM users"))
                .andExpect(jsonPath("$.optimizedQuery").value("SELECT id, name, email FROM users"))
                .andExpect(jsonPath("$.executionTimeMs").value(100))
                .andExpect(jsonPath("$.message.id").value(1))
                .andExpect(jsonPath("$.message.content").value("Optimized query"))
                .andExpect(jsonPath("$.message.fromUser").value(false));
    }

    @Test
    @WithMockUser(username = "testuser")
    public void getQueryHistory_ValidChatId_ReturnsOk() throws Exception {
        // Устанавливаем CustomUserDetails перед запросом
        setupSecurityContext();

        List<SqlQueryResponse> history = Arrays.asList(
                SqlQueryResponse.builder()
                        .id(1L)
                        .originalQuery("SELECT * FROM users")
                        .optimizedQuery("SELECT id, name, email FROM users")
                        .executionTimeMs(100L)
                        .createdAt(LocalDateTime.now())
                        .build(),
                SqlQueryResponse.builder()
                        .id(2L)
                        .originalQuery("SELECT * FROM orders")
                        .optimizedQuery("SELECT id, user_id, total FROM orders")
                        .executionTimeMs(150L)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        when(sqlOptimizationService.getQueryHistory(eq(1L), any(Long.class))).thenReturn(history);

        mockMvc.perform(get("/sql/history/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].originalQuery").value("SELECT * FROM users"))
                .andExpect(jsonPath("$[0].optimizedQuery").value("SELECT id, name, email FROM users"))
                .andExpect(jsonPath("$[0].executionTimeMs").value(100))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].originalQuery").value("SELECT * FROM orders"))
                .andExpect(jsonPath("$[1].optimizedQuery").value("SELECT id, user_id, total FROM orders"))
                .andExpect(jsonPath("$[1].executionTimeMs").value(150));
    }
}
