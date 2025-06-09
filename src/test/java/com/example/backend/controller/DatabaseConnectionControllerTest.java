package com.example.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.backend.config.TestConfig;
import com.example.backend.config.TestSecurityConfig;
import com.example.backend.model.dto.DatabaseConnectionDto;
import com.example.backend.security.WithTestUser;
import com.example.backend.service.DatabaseConnectionService;

@WebMvcTest(DatabaseConnectionController.class)
@Import({TestConfig.class, TestSecurityConfig.class})
@ActiveProfiles("test")
public class DatabaseConnectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DatabaseConnectionService databaseConnectionService;

    @Test
    @WithTestUser
    public void getConnectionsForChat_ValidId_ReturnsOk() throws Exception {
        List<DatabaseConnectionDto> connections = Arrays.asList(
            DatabaseConnectionDto.builder().id(1L).name("Connection 1").build(),
            DatabaseConnectionDto.builder().id(2L).name("Connection 2").build()
        );

        when(databaseConnectionService.getConnectionsForChat(eq(1L))).thenReturn(connections);

        mockMvc.perform(get("/connections/chat/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Connection 1"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("Connection 2"));
    }

    @Test
    @WithTestUser
    public void createConnection_ValidRequest_ReturnsOk() throws Exception {
        DatabaseConnectionDto connectionDto = DatabaseConnectionDto.builder()
                .id(1L)
                .chatId(1L)
                .name("New Connection")
                .dbType("postgresql")
                .host("localhost")
                .port(5432)
                .databaseName("testdb")
                .username("testuser")
                .password("testpass")
                .build();

        when(databaseConnectionService.createConnection(eq(1L), any(DatabaseConnectionDto.class))).thenReturn(connectionDto);

        mockMvc.perform(post("/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chatId\":1,\"name\":\"New Connection\",\"dbType\":\"postgresql\",\"host\":\"localhost\",\"port\":5432,\"databaseName\":\"testdb\",\"username\":\"testuser\",\"password\":\"testpass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("New Connection"));
    }

    @Test
    @WithTestUser
    public void testConnection_ValidRequest_ReturnsOk() throws Exception {
        when(databaseConnectionService.testConnection(any(DatabaseConnectionDto.class))).thenReturn(true);

        mockMvc.perform(post("/connections/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chatId\":1,\"name\":\"Test Connection\",\"dbType\":\"postgresql\",\"host\":\"localhost\",\"port\":5432,\"databaseName\":\"testdb\",\"username\":\"testuser\",\"password\":\"testpass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithTestUser
    public void closeConnection_ValidId_ReturnsNoContent() throws Exception {
        mockMvc.perform(post("/connections/1/close"))
                .andExpect(status().isNoContent());
    }
} 