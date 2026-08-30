package com.ptit.poker.social.chat.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "chat_messages", uniqueConstraints = @UniqueConstraint(
        name = "uk_chat_messages_client_command",
        columnNames = {"room_id", "sender_user_id", "client_message_id"}))
public class ChatMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "sender_user_id", nullable = false)
    private Long senderUserId;

    @Column(name = "client_message_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String clientMessageId;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessageEntity() { }

    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public Long getSenderUserId() { return senderUserId; }
    public String getClientMessageId() { return clientMessageId; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
