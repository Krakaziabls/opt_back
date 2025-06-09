package com.example.backend.controller;

import com.example.backend.config.TestConfig;
import com.example.backend.config.TestJwtConfig;
import com.example.backend.config.TestSecurityConfig;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.model.dto.MessageDto;
import com.example.backend.model.dto.SqlQueryRequest;
import com.example.backend.model.dto.SqlQueryResponse;
import com.example.backend.model.entity.User;
import com.example.backend.security.CustomUserDetails;
import com.example.backend.service.SqlOptimizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SqlOptimizationController.class)
@Import({TestConfig.class, TestSecurityConfig.class, TestJwtConfig.class})
@ActiveProfiles("test")
public class SqlOptimizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SqlOptimizationService sqlOptimizationService;

    private CustomUserDetails userDetails;

    @BeforeEach
    public void setUp() {
        SecurityContextHolder.clearContext();

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("password");

        userDetails = new CustomUserDetails(user);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    public void optimizeQuery_ValidRequest_ReturnsOk() throws Exception {
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
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":1,\"query\":\"SELECT * FROM users\",\"databaseConnectionId\":\"1\"}"))
                .andExpect(request().asyncStarted())
                .andDo(print()) // Для отладки
                .andDo(result -> mockMvc.perform(asyncDispatch(result))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.id").value(1))
                        .andExpect(jsonPath("$.originalQuery").value("SELECT * FROM users"))
                        .andExpect(jsonPath("$.optimizedQuery").value("SELECT id, name, email FROM users"))
                        .andExpect(jsonPath("$.executionTimeMs").value(100))
                        .andExpect(jsonPath("$.message.id").value(1))
                        .andExpect(jsonPath("$.message.content").value("Optimized query"))
                        .andExpect(jsonPath("$.message.fromUser").value(false)));
    }

    @Test
    public void optimizeQuery_ChatNotFound_ReturnsNotFound() throws Exception {
        when(sqlOptimizationService.optimizeQuery(any(Long.class), any(SqlQueryRequest.class)))
                .thenReturn(Mono.error(new ResourceNotFoundException("Chat not found")));

        mockMvc.perform(post("/sql/optimize")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":999,\"query\":\"SELECT * FROM users\",\"databaseConnectionId\":\"1\"}"))
                .andExpect(request().asyncStarted())
                .andDo(print())
                .andDo(result -> mockMvc.perform(asyncDispatch(result))
                        .andExpect(status().isNotFound()));
    }

    @Test
    public void getQueryHistory_ValidChatId_ReturnsOk() throws Exception {
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

        mockMvc.perform(get("/sql/history/1")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].originalQuery").value("SELECT * FROM users"))
                .andExpect(jsonPath("$[0].optimizedQuery").value("SELECT id, name, email FROM users"))
                .andExpect(jsonPath("$[0].executionTimeMs").value(100))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].originalQuery").value("SELECT * FROM orders"))
                .andExpect(jsonPath("$[1].optimizedQuery").value("SELECT id, user_id, total FROM orders"))
                .andExpect(jsonPath("$[1].executionTimeMs").value(150));
    }

    @Test
    public void getQueryHistory_ChatNotFound_ReturnsNotFound() throws Exception {
        when(sqlOptimizationService.getQueryHistory(eq(999L), any(Long.class)))
                .thenThrow(new ResourceNotFoundException("Chat not found"));

        mockMvc.perform(get("/sql/history/999")
                        .with(user(userDetails)))
                .andExpect(status().isNotFound());
    }
}
