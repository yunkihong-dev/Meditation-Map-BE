package com.meditationmap.storage.application.port.out;

public interface ObjectStoragePort {

    StoredObject put(String originalFilename, String contentType, byte[] content);

    record StoredObject(String objectKey, String publicUrl) {}
}
