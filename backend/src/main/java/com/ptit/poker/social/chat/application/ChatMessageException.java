package com.ptit.poker.social.chat.application;

public final class ChatMessageException extends RuntimeException {
    private final String code;

    private ChatMessageException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }

    public static ChatMessageException bad(String code, String message) { return new ChatMessageException(code, message); }
    public static ChatMessageException conflict(String message) { return bad("CHAT_CLIENT_MESSAGE_ID_CONFLICT", message); }
    public static ChatMessageException notFound(String message) { return bad("CHAT_ROOM_NOT_FOUND", message); }
    public static ChatMessageException forbidden(String code, String message) { return bad(code, message); }
}
