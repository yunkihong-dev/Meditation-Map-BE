package com.meditationmap.centerchat.presentation.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** 채팅 요청 본문. 타입·길이 검증은 서비스가 하고 details.fields 로 돌려준다. */
public final class ChatRequests {

    private ChatRequests() {}

    /** programId 는 문자열이 아닐 수도 있어 JsonNode 로 받는다(C3.1 — 문자열 아님 400). */
    public record Start(String placeId, JsonNode programId) {}

    public record Read(String lastReadMessageId) {}

    public record Report(String reason, String detail) {}

    public record Resolve(String action, String note) {}
}
