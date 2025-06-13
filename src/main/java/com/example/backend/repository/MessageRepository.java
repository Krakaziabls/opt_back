package com.example.backend.repository;

import com.example.backend.model.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m LEFT JOIN FETCH m.sqlQuery WHERE m.chat.id = :chatId ORDER BY m.createdAt ASC")
    List<Message> findByChatIdOrderByCreatedAtAsc(Long chatId);

    Optional<Message> findFirstByChatIdOrderByCreatedAtDesc(Long chatId);
}
