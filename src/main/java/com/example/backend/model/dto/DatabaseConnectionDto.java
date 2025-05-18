package com.example.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseConnectionDto {

    private Long id;

    @NotNull(message = "Chat ID is required")
    private Long chatId;

    @NotBlank(message = "Connection name is required")
    private String name;

    @NotBlank(message = "Database type is required")
    private String dbType;

    @NotBlank(message = "Host is required")
    private String host;

    @NotNull(message = "Port is required")
    @Min(value = 1, message = "Port must be greater than 0")
    @Max(value = 65535, message = "Port must be less than 65536")
    private Integer port;

    @NotBlank(message = "Database name is required")
    private String databaseName;

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime lastConnectedAt;
}
