package com.propbuy.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WorkflowActivityService {
    private final JdbcTemplate db;

    public WorkflowActivityService(JdbcTemplate db) {
        this.db = db;
    }

    public void record(String entityType, Long entityId, String action, Long actorId, String details) {
        db.update("INSERT INTO audit_logs(entity_type, entity_id, action, actor_id, details) VALUES (?,?,?,?,?)",
                entityType, entityId, action, actorId, details);
    }

    public void notifyUser(Long userId, String type, String message) {
        db.update("INSERT INTO notifications(user_id, notification_type, message) VALUES (?,?,?)",
                userId, type, message);
    }
}
