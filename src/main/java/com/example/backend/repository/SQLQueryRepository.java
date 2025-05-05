package com.example.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.Chat;
import com.example.backend.entity.SQLQuery;

import java.util.Optional;
;

public interface SQLQueryRepository extends JpaRepository<SQLQuery, Long> {
    List<SQLQuery> findByChatOrderByCreatedAtDesc(Chat chat);

    // Добавляем этот метод:
    Optional<SQLQuery> findByIdAndChat(Long id, Chat chat);
}
