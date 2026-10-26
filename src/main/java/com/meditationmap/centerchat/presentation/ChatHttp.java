package com.meditationmap.centerchat.presentation;

import com.meditationmap.centerchat.application.ChatMessageService;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.shared.exception.InfrastructureException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

/** 고객·오너·관리자 컨트롤러 공통 HTTP 변환. */
final class ChatHttp {

    private ChatHttp() {}

    static List<ChatMessageService.ImageInput> images(List<MultipartFile> files) {
        List<ChatMessageService.ImageInput> out = new ArrayList<>();
        if (files == null) {
            return out;
        }
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) {
                continue;
            }
            try {
                byte[] bytes = f.getSize() > ChatMessageService.MAX_IMAGE_BYTES ? new byte[0] : f.getBytes();
                out.add(new ChatMessageService.ImageInput(f.getOriginalFilename(), f.getContentType(), f.getSize(), bytes));
            } catch (IOException e) {
                throw new InfrastructureException(ErrorCode.FILE_STORE_FAILED, e);
            }
        }
        return out;
    }

    static <T> ResponseEntity<T> created(boolean created, T body) {
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(body);
    }

    /** 사진(C3.8): inline, private max-age=600, nosniff. */
    static ResponseEntity<byte[]> image(ChatMessageService.ImageBytes img) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=600")
                .header("X-Content-Type-Options", "nosniff")
                .body(img.bytes());
    }
}
