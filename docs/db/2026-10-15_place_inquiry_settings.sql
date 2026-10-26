-- 장소 문의 방식(우리 채팅 / 카카오톡) — 신규 테이블 1개 (기존 테이블 ALTER 없음)
--
-- 2026-10-08_center_chat.sql 다음에 적용한다.
-- 운영은 JPA_DDL_AUTO=validate 라서 이 테이블이 없으면 기동에 실패한다(배포 체크리스트 맨 앞). 적용은 사용자 몫(D2).
--
-- 만든 방법은 앞 파일과 같다: 로컬 MySQL 8.4 에서 Hibernate update 로 만든 뒤 SHOW CREATE TABLE 결과를 옮겼고,
-- `CREATE TABLE IF NOT EXISTS` 로 바꿨다(AUTO_INCREMENT 없음).
--
-- place_id        : places.id 와 같은 값(1:1). 행이 없으면 기본 CHAT
-- mode            : 'CHAT' | 'KAKAO' (VARCHAR — @Enumerated 미사용, CHECK 제약 없음)
-- kakao_type      : 'OPEN_CHAT' | 'CHANNEL' | NULL — CHAT 일 때도 오너가 입력해 둔 값을 보관한다
-- kakao_url       : https://open.kakao.com/o/<id> | https://pf.kakao.com/<id>[/chat] (앱에서 검증)
-- owner_member_id : 이 설정을 저장한 오너. 지금 오너(place_owners)와 다르면 설정을 무시하고 CHAT 으로 본다(이관·해제 대비)

CREATE TABLE IF NOT EXISTS `place_inquiry_settings` (
  `place_id` varchar(255) NOT NULL,
  `kakao_type` varchar(16) DEFAULT NULL,
  `kakao_url` varchar(500) DEFAULT NULL,
  `mode` varchar(16) NOT NULL,
  `owner_member_id` char(36) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`place_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
