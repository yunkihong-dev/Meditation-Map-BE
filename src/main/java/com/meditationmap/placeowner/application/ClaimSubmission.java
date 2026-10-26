package com.meditationmap.placeowner.application;

/**
 * 신청 입력(멀티파트 텍스트 파트 + 선택 증빙). 컨트롤러가 받은 그대로 넘기고 검증은 서비스가 한다.
 *
 * @param evidence 없으면 null
 */
public record ClaimSubmission(
        String placeId, String contact, String memo, String businessRegistrationNumber, EvidenceFile evidence) {

    public record EvidenceFile(String originalFilename, String contentType, long size, byte[] bytes) {}
}
