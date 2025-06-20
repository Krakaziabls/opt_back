package com.example.backend.repository;

import com.example.backend.model.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    List<Chat> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<Chat> findByIdAndUserId(Long id, Long userId);
}
