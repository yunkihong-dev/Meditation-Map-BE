package com.meditationmap.storage.application.port.out;

public interface ObjectStoragePort {

    StoredObject put(String originalFilename, String contentType, byte[] content);

    /**
     * 비공개 객체 저장 — 키는 {@code private/{namespace}/{날짜}/{uuid}-{name}}. 공개 URL 을 만들지 않는다
     * ({@code publicUrl} 은 null). 읽기는 {@link #get} 으로 BE 가 대신한다.
     */
    StoredObject putPrivate(String namespace, String originalFilename, String contentType, byte[] content);

    /** 객체 바이트 읽기. 없으면 null. */
    ObjectContent get(String objectKey);

    record StoredObject(String objectKey, String publicUrl) {}

    record ObjectContent(byte[] bytes, String contentType) {}
}
