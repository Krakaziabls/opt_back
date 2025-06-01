package com.example.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketMessageHandler webSocketMessageHandler;

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-scheduler-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue", "/user")
              .setHeartbeatValue(new long[] {10000, 10000}) // 10 секунд для клиента и сервера
              .setTaskScheduler(taskScheduler()); // Используем наш TaskScheduler
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:3000")
                .withSockJS()
                .setDisconnectDelay(30 * 1000)
                .setHeartbeatTime(25 * 1000)
                .setSessionCookieNeeded(false)
                .setWebSocketEnabled(true)
                .setStreamBytesLimit(512 * 1024)
                .setHttpMessageCacheSize(1000);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(128 * 1024) // 128KB
                .setSendBufferSizeLimit(512 * 1024) // 512KB
                .setSendTimeLimit(20000) // 20 секунд
                .setTimeToFirstMessage(30000); // 30 секунд до первого сообщения
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketMessageHandler)
                   .taskExecutor()
                   .corePoolSize(4)
                   .maxPoolSize(8)
                   .queueCapacity(100);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                   .corePoolSize(4)
                   .maxPoolSize(8)
                   .queueCapacity(100);
    }
}
