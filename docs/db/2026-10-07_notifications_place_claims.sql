-- 알림(SSE·FCM) · 광고 수신 동의 · 장소 소유 신청 · 장소 오너 — 신규 테이블 6개 (기존 테이블 ALTER 없음)
--
-- 왜 이 파일이 있나
--   운영은 JPA_DDL_AUTO=validate 이고 마이그레이션 도구가 없다. 테이블 없이 배포하면 기동 검증에 실패해
--   서비스 전체가 내려간다. 배포 "전에" 운영 RDS 에 이 SQL 을 적용해야 한다(D2 — 적용은 사용자 몫).
--
-- 어떻게 만들었나 (손으로 쓰지 않았다)
--   로컬 MySQL 8.4.11(docker compose, 운영과 같은 8.4 · utf8mb4_0900_ai_ci)에서 Hibernate ddl-auto=update 로
--   테이블을 만든 뒤 `SHOW CREATE TABLE` 결과를 그대로 옮겼다. 바꾼 것은 두 가지뿐이다.
--     1) 로컬 데이터가 남긴 `AUTO_INCREMENT=N` 테이블 옵션 제거
--     2) `CREATE TABLE` → `CREATE TABLE IF NOT EXISTS` (재실행해도 안전하게)
--   place_claims.status 의 CHECK 제약은 Hibernate 가 enum 값으로 만든 것이다. 상태를 추가하면 이 제약도 바꿔야 한다.
--
-- 적용 순서: 아무 순서나 가능(FK 없음 — 기존 테이블과 같은 관례).
-- 되돌리기: 새 코드가 내려간 뒤 DROP TABLE 6개. 기존 테이블에는 영향이 없다.

CREATE TABLE IF NOT EXISTS `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `body` varchar(1000) NOT NULL,
  `broadcast_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `link_url` varchar(300) DEFAULT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `recipient_member_id` char(36) NOT NULL,
  `ref_id` varchar(64) DEFAULT NULL,
  `ref_type` varchar(30) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `type` varchar(40) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_notifications_recipient_id` (`recipient_member_id`,`id`),
  KEY `ix_notifications_recipient_unread` (`recipient_member_id`,`read_at`),
  KEY `ix_notifications_broadcast` (`broadcast_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE IF NOT EXISTS `push_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `last_seen_at` datetime(6) NOT NULL,
  `member_id` char(36) NOT NULL,
  `platform` varchar(16) NOT NULL,
  `token` varchar(512) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_push_tokens_token` (`token`),
  KEY `ix_push_tokens_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE IF NOT EXISTS `notification_broadcasts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `body` varchar(1000) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `link_url` varchar(300) DEFAULT NULL,
  `recipient_count` int NOT NULL,
  `request_id` char(36) NOT NULL,
  `sent_by_member_id` char(36) NOT NULL,
  `target_type` varchar(16) NOT NULL,
  `title` varchar(200) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_broadcasts_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE IF NOT EXISTS `member_marketing_consents` (
  `member_id` char(36) NOT NULL,
  `agreed_at` datetime(6) DEFAULT NULL,
  `push_agreed` bit(1) NOT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE IF NOT EXISTS `place_claims` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active_key` varchar(300) DEFAULT NULL,
  `applicant_member_id` char(36) NOT NULL,
  `business_registration_number` varchar(20) DEFAULT NULL,
  `contact` varchar(100) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `evidence_content_type` varchar(128) DEFAULT NULL,
  `evidence_file_name` varchar(255) DEFAULT NULL,
  `evidence_object_key` varchar(512) DEFAULT NULL,
  `evidence_size_bytes` bigint DEFAULT NULL,
  `memo` varchar(2000) DEFAULT NULL,
  `place_id` varchar(255) NOT NULL,
  `reject_reason` varchar(500) DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewed_by_member_id` char(36) DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_place_claims_active_key` (`active_key`),
  KEY `ix_place_claims_status_id` (`status`,`id`),
  KEY `ix_place_claims_applicant` (`applicant_member_id`,`id`),
  KEY `ix_place_claims_place_status` (`place_id`,`status`),
  CONSTRAINT `place_claims_chk_1` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'APPROVED',_utf8mb4'REJECTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE IF NOT EXISTS `place_owners` (
  `place_id` varchar(255) NOT NULL,
  `approved_at` datetime(6) NOT NULL,
  `approved_by_member_id` char(36) NOT NULL,
  `claim_id` bigint NOT NULL,
  `owner_member_id` char(36) NOT NULL,
  PRIMARY KEY (`place_id`),
  KEY `ix_place_owners_owner` (`owner_member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

