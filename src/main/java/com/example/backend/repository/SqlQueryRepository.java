package com.example.backend.repository;

import com.example.backend.model.entity.SqlQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SqlQueryRepository extends JpaRepository<SqlQuery, Long> {

    @Query("SELECT sq FROM SqlQuery sq LEFT JOIN FETCH sq.message m LEFT JOIN FETCH sq.databaseConnection WHERE m.chat.id = :chatId ORDER BY sq.createdAt DESC")
    List<SqlQuery> findByMessageChatIdOrderByCreatedAtDesc(Long chatId);
}
