CREATE TABLE chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    sender_user_id BIGINT NOT NULL,
    client_message_id CHAR(36) NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_chat_messages PRIMARY KEY (id),
    CONSTRAINT uk_chat_messages_client_command UNIQUE (room_id, sender_user_id, client_message_id),
    CONSTRAINT chk_chat_messages_content_length CHECK (CHAR_LENGTH(content) BETWEEN 1 AND 500),
    CONSTRAINT fk_chat_messages_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    INDEX idx_chat_messages_room_id (room_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
