# meditation-map-be — 데이터 모델·API 설계 (초안)

`meditation-map-fe`의 도메인 타입(`MeditationPlace`, `MeditationExpert`, `Region`), HTTP 레포지토리 계약, 화면(프로필·공지·찜)까지 고려한 **ERD**와 **RESTful HTTP API** 초안입니다. 스키마·엔드포인트 이름은 구현 시 조정해도 됩니다.

### 구현 구조 (DDD — 바운디드 컨텍스트별)

같은 레이어 이름을 **컨텍스트마다** 반복합니다. 컨텍스트 간 참조는 최소화하고(예: `place`·`expert`가 `region.domain.RegionId`만 참조), 횡단 관심사는 `platform`·`shared`에 둡니다.

| 컨텍스트 / 공용 | 패키지 예시 | 역할 |
|-----------------|-------------|------|
| **region** | `com.meditationmap.region.{domain,application,infrastructure,presentation}` | 지역 |
| **place** | `...place.*` | 명상 장소 |
| **expert** | `...expert.*` | 전문가 |
| **identity** | `...identity.*` | 회원·JWT·인증 유스케이스 |
| **shared** | `com.meditationmap.shared.domain` | 여러 컨텍스트 공용 도메인 베이스(`DomainException` 등) |
| **platform** | `com.meditationmap.platform.{config,web}` | Security, Redis 캐시, OpenAPI, 전역 `@ControllerAdvice` |
| **storage** | `com.meditationmap.storage.*` | MinIO(S3 호환) 객체 업로드·공개 URL |

`MeditationMapApplication`은 `@EntityScan` / `@EnableJpaRepositories`로 `com.meditationmap` 전역을 스캔합니다(JPA 엔티티는 각 컨텍스트의 `infrastructure.jpa`에만 위치).

### 로컬 실행 (Docker MySQL / Redis)

맥/윈도우에 **이미 MySQL이 3306을 쓰는 경우**가 많아, Compose는 MySQL을 **호스트 `3307` → 컨테이너 3306**으로만 노출합니다.

1. `docker compose up -d` (저장소 루트 `meditation-map-be/`) — MySQL **3307**, Redis **6379**, MinIO **9000**(API)·**9001**(콘솔), 계정 `minio` / `minio12345`
2. 앱: `./gradlew bootRun --args='--spring.profiles.active=local'` — DB는 `application-local.yml`, **MinIO는 `app.storage.minio.enabled=true`**
3. 프론트: `VITE_API_BASE_URL=http://localhost:8080`
4. 이미지 등 파일: `POST /storage/objects` (multipart `file`, **JWT 필요**) → 응답 `url`을 place/expert JSON에 넣어 저장하면 됨. 공개 읽기 정책은 로컬 편의용(운영은 presigned 등으로 좁히는 것을 권장).

---

## 1. ERD (논리 모델)

아래는 **정규화 기준** 설계입니다. 운영 편의상 `JSONB` 컬럼으로 일부를 합치는 선택도 가능합니다(각 테이블 설명에 병기).

```mermaid
erDiagram
  regions ||--o{ places : "region_id"
  regions ||--o{ expert_regions : "region_id"
  places ||--o{ place_detail_sections : "place_id"
  places ||--o{ place_programs : "place_id"
  places ||--o{ place_instructors : "place_id"
  places ||--o{ place_hashtags : "place_id"
  places ||--o{ place_themes : "place_id"
  place_programs ||--o{ place_program_images : "program_id"
  place_programs ||--o{ place_program_reviews : "program_id"
  place_instructors ||--o{ place_instructor_reviews : "instructor_id"

  experts ||--o{ expert_regions : "expert_id"
  experts ||--o{ expert_specialties : "expert_id"
  experts ||--o{ expert_degrees : "expert_id"
  experts ||--o{ expert_certificates : "expert_id"
  experts ||--o{ expert_careers : "expert_id"
  experts ||--o{ expert_class_types : "expert_id"
  experts ||--o{ expert_activity_areas : "expert_id"
  experts ||--o{ expert_programs : "expert_id"
  experts ||--o{ expert_reviews : "expert_id"
  experts }o--|| places : "center_place_id (nullable)"

  expert_programs ||--o{ expert_program_class_links : "program_id"

  users ||--o{ oauth_accounts : "user_id"
  users ||--o{ user_favorites : "user_id"
  users ||--o{ inquiries : "user_id (nullable)"
  users }o--o| regions : "preferred_region_id"
  places ||--o{ user_favorites : "place_id"

  users {
    uuid id PK
    string email UK "nullable if 휴대폰-only 정책"
    string phone UK "nullable, E.164 등"
    string password_hash "이메일 로그인 시"
    boolean phone_verified
    uuid preferred_region_id FK "선택, FE 지역 설정"
    boolean agree_service
    boolean agree_privacy
    boolean agree_marketing
    timestamptz created_at
    timestamptz updated_at
  }

  oauth_accounts {
    uuid id PK
    uuid user_id FK
    string provider "kakao|naver|google"
    string provider_user_id
    timestamptz created_at
    UK "provider + provider_user_id"
  }

  regions {
    string id PK "예: KR-11"
    string name
    string slug
    int sort_order "선택"
  }

  places {
    uuid id PK "FE는 string id 허용"
    string region_id FK
    string name
    text short_description
    text description
    string address
    decimal latitude "지도·클러스터용, 권장"
    decimal longitude
    int view_count "인기 정렬, 기본 0"
    decimal rating_avg "집계 캐시, 선택"
    int review_count "집계 캐시, 선택"
    string thumbnail_url
    string admission_fee
    string venue_kind "명상지|명상센터"
    boolean has_temple_stay
    string duration
    string organization_name "organization.name"
    string external_link
    text[] facilities "또는 place_facilities 테이블"
    timestamptz created_at
    timestamptz updated_at
  }

  place_hashtags {
    uuid id PK
    uuid place_id FK
    string tag
  }

  place_themes {
    uuid id PK
    uuid place_id FK
    string theme
  }

  place_detail_sections {
    uuid id PK
    uuid place_id FK
    string title
    text body "마크다운"
    int sort_order
  }

  place_programs {
    uuid id PK
    uuid place_id FK
    string title
    string status "ongoing|past"
    text body_from_venue "마크다운"
    int sort_order
  }

  place_program_images {
    uuid id PK
    uuid program_id FK
    string url
    int sort_order "첫 번째를 대표로 쓰거나 별도 is_primary"
  }

  place_program_reviews {
    uuid id PK
    uuid program_id FK
    string author
    text body
    smallint rating "1-5, optional"
    int sort_order
  }

  place_instructors {
    uuid id PK
    uuid place_id FK
    string name
    string photo_url
    text intro
    int sort_order
  }

  place_instructor_reviews {
    uuid id PK
    uuid instructor_id FK
    string author
    text body
    smallint rating
    int sort_order
  }

  experts {
    uuid id PK
    string name
    string avatar_url
    text intro
    boolean has_center
    text center_summary
    uuid center_place_id FK "nullable"
    timestamptz created_at
    timestamptz updated_at
  }

  expert_regions {
    uuid expert_id FK
    string region_id FK
    PK "expert_id, region_id"
  }

  expert_specialties {
    uuid id PK
    uuid expert_id FK
    string label
    int sort_order
  }

  expert_degrees {
    uuid id PK
    uuid expert_id FK
    text line
    int sort_order
  }

  expert_certificates {
    uuid id PK
    uuid expert_id FK
    text line
    int sort_order
  }

  expert_careers {
    uuid id PK
    uuid expert_id FK
    text line
    int sort_order
  }

  expert_class_types {
    uuid id PK
    uuid expert_id FK
    string label
    int sort_order
  }

  expert_activity_areas {
    uuid id PK
    uuid expert_id FK
    string area_name "has_center=false 일 때"
    int sort_order
  }

  expert_programs {
    uuid id PK
    uuid expert_id FK
    string title
    string status
    text description
    string image_url
    int sort_order
  }

  expert_program_class_links {
    uuid program_id FK
    string class_type_label "classTypes 중 링크, FE linksClassTypes"
    PK "program_id, class_type_label"
  }

  expert_reviews {
    uuid id PK
    uuid expert_id FK
    string author
    text body
    smallint rating
    int sort_order
  }

  user_favorites {
    uuid user_id FK
    uuid place_id FK
    timestamptz created_at
    PK "user_id, place_id"
  }

  notices {
    uuid id PK
    string category
    string title
    date published_on "FE date"
    string summary
    jsonb details "paragraphs, bullets, footer 구조"
    timestamptz created_at
    timestamptz updated_at
  }

  inquiries {
    uuid id PK
    uuid user_id FK "비회원이면 null"
    string email "회신용"
    string subject
    text body
    string status "received|in_progress|answered|closed"
    timestamptz created_at
  }
```

### 1.1 FE DTO와 매핑 메모

| FE 필드 | 저장 위치 |
|--------|----------|
| `MeditationPlace.organization.name` | `places.organization_name` |
| `MeditationPlace.programs[].imageUrl` | 프로그램의 첫 이미지 또는 `image_url` 컬럼(단일만 쓸 때) |
| `MeditationPlace.programs[].imageUrls[]` | `place_program_images` |
| `PlaceDto` 전체 | `GET /places`, `GET /places/:id` 응답이 현재 FE 타입과 동일한 JSON 트리로 직렬화 |
| `MeditationExpert.regionIds[]` | `expert_regions` 조인 결과 배열 |
| `MeditationExpert.programs[].linksClassTypes` | `expert_program_class_links` |
| 지도 좌표 | FE는 `placeApproxPosition`으로 **가짜 좌표** 사용 중 → 백엔드에 `latitude`/`longitude` 두면 네이버 지도와 일치 |

### 1.2 JSONB로 단순화 가능한 부분

- `place_detail_sections`, `place_program_reviews` 등을 `places.detail_sections jsonb`처럼 묶을 수 있음. 검색·필터를 DB에서 하려면 텍스트 컬럼/FTS 인덱스 설계가 필요합니다.

---

## 2. HTTP API 개요

- **Base URL**: 환경변수 `VITE_API_BASE_URL`과 동일한 호스트 (예: `https://api.example.com`). 경로는 버전 prefix 권장: `/v1/...` (아래 표는 **버전 없이** 적음; 구현 시 `/v1` 추가).
- **Content-Type**: `application/json; charset=utf-8`
- **인증**: 회원 전용 엔드포인트는 `Authorization: Bearer <access_token>` (구현 세부는 생략).
- **공통 오류**: `400` 잘못된 입력, `401` 미인증, `403` 권한, `404` 없음, `409` 충돌, `422` 검증 실패(바디에 필드별 메시지 권장).

---

## 3. 명상 장소 (Places) — FE와 동일 계약

프론트 `HttpPlacesRepository`가 기대하는 동작입니다.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/places` | 전체 장소 목록. JSON 배열, 각 요소는 `PlaceDto`(`MeditationPlace`). |
| `GET` | `/places?regionId={regionId}` | 지역 필터. `regionId=all`이면 전체와 동일하게 처리하는 것을 권장(쿼리 생략과 동일). |
| `GET` | `/places/{id}` | 단건. 없으면 **404**. |

### 3.1 권장 확장 (서버 필터·페이지네이션)

현재 필터·정렬·페이지는 FE `meditationService`에서 메모리 상으로 처리합니다. 데이터가 커지면 서버로 이전합니다.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/places` | 쿼리: `regionId`, `category`, `tags` (반복 또는 콤마), `keyword`, `sortBy=recommend\|name`, `page`, `pageSize`. 응답: `{ items, totalItems, totalPages, page, pageSize }` (`PaginationResult<PlaceDto>`). |
| `GET` | `/places/popular` | 쿼리: `limit` (기본 8). `view_count` 내림차순. |

### 3.2 지도·뷰 카운트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/places/map` | 쿼리: `minLat`, `maxLat`, `minLng`, `maxLng` (뷰포트 bbox). 경량 DTO( id, name, lat, lng, thumbnailUrl … ) 반환 권장. |
| `POST` | `/places/{id}/views` | 상세 진입 시 조회수 증가(멱등 키 또는 쿠키/세션으로 중복 완화 가능). 응답: `{ viewCount: number }`. |

### 3.3 관리자(CMS) — 선택

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/admin/places` | 장소 생성 |
| `PATCH` | `/admin/places/{id}` | 수정 |
| `DELETE` | `/admin/places/{id}` | 삭제(soft delete 권장) |

---

## 4. 지역 (Regions)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/regions` | FE `HttpRegionsRepository`: `Region[]` (`id`, `name`, `slug`). |

---

## 5. 전문가 (Experts)

프론트 `HttpExpertsRepository` 계약.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/experts` | 전체. JSON 배열 `ExpertDto`(`MeditationExpert`). |
| `GET` | `/experts?regionId={regionId}` | `regionIds`에 해당 지역 포함된 전문가만. `all`이면 전체. |
| `GET` | `/experts/{id}` | 단건. 없으면 **404**. |

### 5.1 권장 확장

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/experts/{expertId}/programs/{programId}` | 클래스 상세 전용(현재 FE는 로컬 데이터로 전문가 객체에서 찾음). 선택. |

---

## 6. 인증·사용자 (Profile 화면 대응)

프론트는 이메일·비밀번호, 휴대폰 인증 UI, 카카오·네이버·구글 버튼을 포함합니다. 실제 연동 시 대략 다음 API가 필요합니다.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/auth/register/email` | body: `email`, `password`, 약관 동의 플래그. 인증 메일 또는 바로 토큰 발급(정책에 따름). |
| `POST` | `/auth/login/email` | body: `email`, `password` → `accessToken`, `refreshToken`, `user`. |
| `POST` | `/auth/logout` | 리프레시 토큰 무효화 등. |
| `POST` | `/auth/refresh` | body: `refreshToken`. |
| `POST` | `/auth/phone/send-code` | body: `phone`. SMS 인증번호 발송. |
| `POST` | `/auth/phone/verify` | body: `phone`, `code`. |
| `GET` | `/oauth2/authorization/{registrationId}` | Spring Security OAuth2 Client. `registrationId`: `google` \| `kakao`. 브라우저가 백엔드(예: `http://localhost:8080`)로 이동해 로그인 플로우 시작. |
| (리다이렉트) | `/login/oauth2/code/{registrationId}` | IdP가 코드와 함께 호출. **Google/Kakao 콘솔에 이 URI를 그대로 등록** (예: `http://localhost:8080/login/oauth2/code/google`). 성공 시 `app.oauth2.frontend-callback-url`로 JWT·이메일이 쿼리로 전달됨. |

**환경 변수 (백엔드):** `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, 선택 `OAUTH2_FRONTEND_CALLBACK_URL` (기본값 `http://localhost:3000/auth/oauth`).

### 6.1 내 프로필

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/me` | 현재 사용자 프로필(이메일 마스킹 정책 등). |
| `PATCH` | `/me` | 닉네임, `preferredRegionId`, 마케팅 수신 등. |
| `PATCH` | `/me/password` | 기존 비밀번호 + 새 비밀번호. |

---

## 7. 찜 (Favorites)

현재 FE는 `localStorage`만 사용. 로그인 연동 시 서버 동기화용.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/me/favorites` | 찜한 `place_id` 목록 또는 `PlaceDto` 요약 배열. |
| `PUT` | `/me/favorites` | body: `{ placeIds: string[] }` 전체 치환(또는 `PATCH`로 증분). |
| `POST` | `/me/favorites/{placeId}` | 추가. |
| `DELETE` | `/me/favorites/{placeId}` | 제거. |

---

## 8. 공지사항 (Notices)

현재 `src/data/notices.json` 구조: `id`, `category`, `title`, `date`, `summary`, `details`.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/notices` | 목록. 쿼리: `q`(제목 검색), `page`, `pageSize`. |
| `GET` | `/notices/{id}` | 상세(본문 `details` 포함). |

### 8.1 관리자 — 선택

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/admin/notices` | 생성 |
| `PATCH` | `/admin/notices/{id}` | 수정 |
| `DELETE` | `/admin/notices/{id}` | 삭제 |

---

## 9. 1:1 문의 (Inquiry)

현재 페이지는 이메일 안내만. 폼을 붙일 경우:

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/inquiries` | body: `email`, `subject`, `body`. 선택: `Authorization`으로 `user_id` 연결. |
| `GET` | `/admin/inquiries` | 관리자 목록(상태 필터). |
| `PATCH` | `/admin/inquiries/{id}` | 상태·내부 메모 업데이트. |

---

## 10. 응답 DTO 참고 (FE 호환)

### 10.1 `PlaceDto` / `MeditationPlace`

`meditation-map-fe`의 `src/services/meditation/types.ts`에 정의된 필드를 그대로 만족시키면 됩니다. 주요 필드:

- `id`, `regionId`, `name`, `viewCount?`, `rating?`, `reviewCount?`, `shortDescription`, `description`, `address`, `thumbnailUrl`, `hashtags[]`, `themes[]`, `hasTempleStay`, `duration`, `admissionFee?`, `venueKind?`, `programs?`, `instructors?`, `organization: { name }`, `externalLink`, `detailSections[]`, `facilities?`

### 10.2 `ExpertDto` / `MeditationExpert`

- `id`, `name`, `avatarUrl`, `specialties[]`, `regionIds[]`, `intro`, `degrees[]`, `certificates[]`, `careers[]`, `classTypes[]`, `hasCenter`, `centerSummary?`, `centerPlaceId?`, `activityAreas?`, `programs[]`, `reviews[]`

### 10.3 `Region`

- `id`, `name`, `slug`

---

## 11. 구현 시 체크리스트

- CORS: FE 오리진 허용, credentials 사용 시 쿠키·헤더 명시.
- `PlaceDto`가 크므로 목록 API에서는 썸네일·요약 필드만 내려주는 **리스트용 DTO** 분리를 검토.
- 지오쿼리: PostGIS 또는 위도/경도 인덱스 + bbox 필터.
- 조회수·인기: 봇·새로고침 남용 방지 레이트 리밑.
- 프로필 OAuth는 각 플랫폼 앱 키·리다이렉트 URI 등록 필수.

이 문서는 초안이며, 실제 백엔드 스택(예: Nest, Spring, Django)에 맞춰 마이그레이션과 OpenAPI(Swagger) 생성을 권장합니다.
