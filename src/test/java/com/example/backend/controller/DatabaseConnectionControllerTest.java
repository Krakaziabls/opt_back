package com.example.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.backend.config.TestConfig;
import com.example.backend.config.TestJwtConfig;
import com.example.backend.config.TestSecurityConfig;
import com.example.backend.model.dto.DatabaseConnectionDto;
import com.example.backend.repository.ChatRepository;
import com.example.backend.repository.DatabaseConnectionRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.DatabaseConnectionService;

@WebMvcTest(DatabaseConnectionController.class)
@Import({TestConfig.class, TestSecurityConfig.class, TestJwtConfig.class})
@ActiveProfiles("test")
public class DatabaseConnectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DatabaseConnectionService databaseConnectionService;

    @MockBean
    private DatabaseConnectionRepository databaseConnectionRepository;

    @MockBean
    private ChatRepository chatRepository;

    @MockBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(username = "testuser")
    public void getConnectionsForChat_ValidId_ReturnsOk() throws Exception {
        List<DatabaseConnectionDto> connections = Arrays.asList(
            DatabaseConnectionDto.builder()
                .id(1L)
                .name("Connection 1")
                .dbType("postgresql")
                .host("localhost")
                .port(5432)
                .databaseName("db1")
                .username("user1")
                .password("pass1")
                .build(),
            DatabaseConnectionDto.builder()
                .id(2L)
                .name("Connection 2")
                .dbType("postgresql")
                .host("localhost")
                .port(5432)
                .databaseName("db2")
                .username("user2")
                .password("pass2")
                .build()
        );

        when(databaseConnectionService.getConnectionsForChat(eq(1L))).thenReturn(connections);

        mockMvc.perform(get("/connections/chat/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Connection 1"))
                .andExpect(jsonPath("$[0].dbType").value("postgresql"))
                .andExpect(jsonPath("$[0].host").value("localhost"))
                .andExpect(jsonPath("$[0].port").value(5432))
                .andExpect(jsonPath("$[0].databaseName").value("db1"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("Connection 2"))
                .andExpect(jsonPath("$[1].dbType").value("postgresql"))
                .andExpect(jsonPath("$[1].host").value("localhost"))
                .andExpect(jsonPath("$[1].port").value(5432))
                .andExpect(jsonPath("$[1].databaseName").value("db2"));
    }

    @Test
    @WithMockUser(username = "testuser")
    public void createConnection_ValidRequest_ReturnsOk() throws Exception {
        DatabaseConnectionDto connectionDto = DatabaseConnectionDto.builder()
                .id(1L)
                .name("New Connection")
                .dbType("postgresql")
                .host("localhost")
                .port(5432)
                .databaseName("db")
                .username("user")
                .password("pass")
                .build();

        when(databaseConnectionService.createConnection(any(Long.class), any(DatabaseConnectionDto.class)))
                .thenReturn(connectionDto);

        mockMvc.perform(post("/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Connection\",\"dbType\":\"postgresql\",\"host\":\"localhost\",\"port\":5432,\"databaseName\":\"db\",\"username\":\"user\",\"password\":\"pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("New Connection"))
                .andExpect(jsonPath("$.dbType").value("postgresql"))
                .andExpect(jsonPath("$.host").value("localhost"))
                .andExpect(jsonPath("$.port").value(5432))
                .andExpect(jsonPath("$.databaseName").value("db"));
    }

    @Test
    @WithMockUser(username = "testuser")
    public void testConnection_ValidRequest_ReturnsOk() throws Exception {
        Map<String, Boolean> response = new HashMap<>();
        response.put("success", true);

        when(databaseConnectionService.testConnection(any(DatabaseConnectionDto.class)))
                .thenReturn(true);

        mockMvc.perform(post("/connections/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test Connection\",\"dbType\":\"postgresql\",\"host\":\"localhost\",\"port\":5432,\"databaseName\":\"db\",\"username\":\"user\",\"password\":\"pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "testuser")
    public void closeConnection_ValidId_ReturnsNoContent() throws Exception {
        mockMvc.perform(post("/connections/1/close"))
                .andExpect(status().isNoContent());
    }
} 