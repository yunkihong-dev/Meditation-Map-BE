-- 센터 1:1 문의(고객 ↔ 센터 오너 채팅) — 신규 테이블 4개 (기존 테이블 ALTER 없음, notifications 인덱스 추가 없음)
--
-- 2026-10-07_notifications_place_claims.sql · 2026-10-07b_place_stats_business_info.sql 다음에 적용한다.
-- 운영은 JPA_DDL_AUTO=validate 라서 이 테이블이 없으면 기동에 실패한다(배포 체크리스트 맨 앞). 적용은 사용자 몫(D2).
--
-- 만든 방법은 앞 파일과 같다: 로컬 MySQL 8.4 에서 Hibernate update 로 만든 뒤 SHOW CREATE TABLE 결과를 옮겼고,
-- `CREATE TABLE IF NOT EXISTS` 로 바꾸고 테이블 옵션의 AUTO_INCREMENT=n 만 지웠다.
--
-- center_chat_threads.open_key   : '{placeId}|{customerId}|{programId 또는 -}' 유니크 — 같은 고객·장소·프로그램 대화 1개(동시 시작 포함)
-- center_chat_messages.uk_ccm_client : (thread_id, sender_role, client_message_id) — 전송 연타·재시도 멱등
-- center_chat_messages.deleted_at/deleted_by_member_id : 본인 메시지 소프트 삭제(DC5). 원문은 남는다(신고 처리 시 관리자만 열람)
-- center_chat_reports.active_key : PENDING 일 때만 '{threadId}:{reporterId}' — 한 사람당 대기 신고 1건
-- center_chat_admin_access_logs  : 관리자 열람 감사 로그(DC3). 조회 API 없음

CREATE TABLE IF NOT EXISTS `center_chat_threads` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `blocked_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `customer_last_read_message_id` bigint DEFAULT NULL,
  `customer_member_id` char(36) NOT NULL,
  `last_message_at` datetime(6) DEFAULT NULL,
  `last_message_id` bigint DEFAULT NULL,
  `last_sender_role` varchar(16) DEFAULT NULL,
  `open_key` varchar(400) DEFAULT NULL,
  `owner_last_read_message_id` bigint DEFAULT NULL,
  `place_id` varchar(255) NOT NULL,
  `program_id` varchar(100) DEFAULT NULL,
  `program_title` varchar(200) DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_center_chat_threads_open_key` (`open_key`),
  KEY `ix_cct_customer_last` (`customer_member_id`,`last_message_id`),
  KEY `ix_cct_place_last` (`place_id`,`last_message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE IF NOT EXISTS `center_chat_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `body` varchar(2000) DEFAULT NULL,
  `client_message_id` char(36) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `deleted_by_member_id` char(36) DEFAULT NULL,
  `images` json DEFAULT NULL,
  `sender_member_id` char(36) NOT NULL,
  `sender_role` varchar(16) NOT NULL,
  `thread_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ccm_client` (`thread_id`,`sender_role`,`client_message_id`),
  KEY `ix_ccm_thread_id` (`thread_id`,`id`),
  KEY `ix_ccm_thread_sender_id` (`thread_id`,`sender_role`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE IF NOT EXISTS `center_chat_reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active_key` varchar(80) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `detail` varchar(1000) DEFAULT NULL,
  `reason` varchar(32) NOT NULL,
  `reported_up_to_message_id` bigint NOT NULL,
  `reporter_member_id` char(36) NOT NULL,
  `reporter_role` varchar(16) NOT NULL,
  `resolution_action` varchar(16) DEFAULT NULL,
  `resolution_note` varchar(500) DEFAULT NULL,
  `resolved_at` datetime(6) DEFAULT NULL,
  `resolved_by_member_id` char(36) DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `thread_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ccr_active_key` (`active_key`),
  KEY `ix_ccr_status_id` (`status`,`id`),
  KEY `ix_ccr_thread` (`thread_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE IF NOT EXISTS `center_chat_admin_access_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `access_kind` varchar(16) NOT NULL,
  `accessed_at` datetime(6) NOT NULL,
  `admin_member_id` char(36) NOT NULL,
  `thread_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_ccaal_thread` (`thread_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

