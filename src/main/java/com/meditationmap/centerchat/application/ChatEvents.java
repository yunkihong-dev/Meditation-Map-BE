package com.meditationmap.centerchat.application;

/** 커밋 뒤 SSE chat-* 전송 트리거(채팅 C7.1). 롤백되면 나가지 않는다. */
public final class ChatEvents {

    private ChatEvents() {}

    public record MessageCreated(Long threadId, Long messageId) {}

    public record MessageDeleted(Long threadId, Long messageId) {}

    /** readerRole 쪽 읽음이 lastReadMessageId 로 올라감. */
    public record ReadRaised(Long threadId, String readerRole, Long lastReadMessageId) {}

    public record ThreadStatusChanged(Long threadId, String status) {}
}
