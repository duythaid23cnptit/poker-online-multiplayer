CREATE TABLE admin_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_user_id BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT NULL,
    reason VARCHAR(500) NULL,
    request_id VARCHAR(100) NULL,
    metadata_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_admin_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_admin_audit_log_admin_user FOREIGN KEY (admin_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_admin_audit_log_action_type CHECK (action_type IN (
        'USER_SUSPENDED', 'USER_REACTIVATED', 'PLAYER_REMOVED_FROM_ROOM', 'ROOM_CLOSED', 'GAME_TERMINATED'
    )),
    CONSTRAINT chk_admin_audit_log_target_type CHECK (target_type IN ('USER', 'ROOM', 'GAME_SESSION')),
    INDEX idx_admin_audit_log_admin_created (admin_user_id, created_at),
    INDEX idx_admin_audit_log_target_created (target_type, target_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
