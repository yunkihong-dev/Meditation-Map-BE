-- 라운드 3: 장소 통계(조회·북마크) · 비공개 사업자 정보 — 신규 테이블 4개 (기존 테이블 ALTER 없음)
--
-- 2026-10-07_notifications_place_claims.sql 다음에 적용한다(순서 의존은 없지만 같은 배포에 함께 필요하다).
-- 운영은 JPA_DDL_AUTO=validate 라서 이 테이블이 없으면 기동에 실패한다. 적용은 사용자 몫(D2).
--
-- 만든 방법은 앞 파일과 같다: 로컬 MySQL 8.4.11 에서 Hibernate update 로 만든 뒤 SHOW CREATE TABLE 결과를 옮겼고,
-- `CREATE TABLE IF NOT EXISTS` 로만 바꿨다(AUTO_INCREMENT 컬럼 없음).
--
-- place_view_dedup 은 조회 중복 억제용이다(조회자 해시 = SHA-256, 원문 IP 저장 안 함). 앱이 매일 KST 04:10 에
-- 어제보다 오래된 행을 지운다.

CREATE TABLE IF NOT EXISTS `place_view_daily` (
  `place_id` varchar(255) NOT NULL,
  `stat_date` date NOT NULL,
  `view_count` int NOT NULL,
  PRIMARY KEY (`place_id`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE IF NOT EXISTS `place_view_dedup` (
  `place_id` varchar(255) NOT NULL,
  `stat_date` date NOT NULL,
  `viewer_hash` char(64) NOT NULL,
  PRIMARY KEY (`place_id`,`stat_date`,`viewer_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE IF NOT EXISTS `place_bookmark_daily` (
  `place_id` varchar(255) NOT NULL,
  `stat_date` date NOT NULL,
  `added_count` int NOT NULL,
  `removed_count` int NOT NULL,
  PRIMARY KEY (`place_id`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE IF NOT EXISTS `place_business_infos` (
  `place_id` varchar(255) NOT NULL,
  `business_registration_number` varchar(20) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by_member_id` char(36) DEFAULT NULL,
  PRIMARY KEY (`place_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

