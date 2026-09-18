package com.devdhar.taskmanager.repository;

import com.devdhar.taskmanager.entity.AgentInteractionLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AgentInteractionLogRepository extends MongoRepository<AgentInteractionLog, String> {
    List<AgentInteractionLog> findByUserIdOrderByTimestampDesc(Long userId);
}
