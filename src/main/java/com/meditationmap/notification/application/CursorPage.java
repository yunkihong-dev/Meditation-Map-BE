package com.meditationmap.notification.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 커서 페이지 응답 (계약 0절). 마지막 페이지면 nextCursor 는 null. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CursorPage<T>(List<T> items, String nextCursor) {}
