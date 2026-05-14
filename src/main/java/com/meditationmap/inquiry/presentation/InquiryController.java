package com.meditationmap.inquiry.presentation;

import com.meditationmap.inquiry.application.InquiryApplicationService;
import com.meditationmap.inquiry.presentation.dto.CreateInquiryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Inquiries")
@RestController
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryApplicationService inquiryApplicationService;

    @Operation(summary = "1:1 문의 등록 (로그인 시 Authorization: Bearer 있으면 사용자와 연결)")
    @PostMapping("/inquiries")
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody CreateInquiryRequest body, Authentication authentication) {
        inquiryApplicationService.submit(body.email(), body.subject(), body.body(), authentication);
    }
}
