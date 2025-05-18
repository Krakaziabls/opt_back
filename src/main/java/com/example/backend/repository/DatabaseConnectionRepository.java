package com.example.backend.repository;

import com.example.backend.model.entity.DatabaseConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatabaseConnectionRepository extends JpaRepository<DatabaseConnection, Long> {

    List<DatabaseConnection> findByChatIdAndActiveTrue(Long chatId);

    Optional<DatabaseConnection> findByIdAndChatId(Long id, Long chatId);

    void deleteAllByChatId(Long chatId);
}
