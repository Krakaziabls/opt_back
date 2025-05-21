package com.example.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.example.backend.config.TestConfig;
import com.example.backend.config.TestJwtConfig;
import com.example.backend.config.TestSecurityConfig;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestConfig.class, TestSecurityConfig.class, TestJwtConfig.class})
@TestPropertySource(properties = {
    "spring.main.allow-bean-definition-overriding=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true",
    "spring.jpa.properties.hibernate.format_sql=true",
    "spring.flyway.enabled=false"
})
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
