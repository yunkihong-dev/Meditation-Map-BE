package com.meditationmap.region.domain;

import java.util.List;

/**
 * 대한민국 시·도 고정 목록 (지도 SVG `KR-*` ID 와 동일).
 *
 * <p>행정구역은 서비스가 정하는 값이 아니라 바깥에서 주어지는 상수다. 운영자가 관리 화면에서
 * 넣고 빼는 데이터가 아니므로 코드에 두고 기동 시 테이블에 맞춘다. FE 의
 * {@code src/data/koreaRegions.ts} 와 id·name·slug 가 1:1 로 같아야 한다.
 */
public final class KoreaRegions {

    /** 시·도 한 건. sortOrder 는 아래 선언 순서를 그대로 쓴다. */
    public record Seed(String id, String name, String slug, int sortOrder) {}

    private static final List<Seed> ALL =
            List.of(
                    new Seed("KR-11", "서울", "seoul", 0),
                    new Seed("KR-26", "부산", "busan", 1),
                    new Seed("KR-27", "대구", "daegu", 2),
                    new Seed("KR-28", "인천", "incheon", 3),
                    new Seed("KR-29", "광주", "gwangju", 4),
                    new Seed("KR-30", "대전", "daejeon", 5),
                    new Seed("KR-31", "울산", "ulsan", 6),
                    new Seed("KR-41", "경기", "gyeonggi", 7),
                    new Seed("KR-42", "강원", "gangwon", 8),
                    new Seed("KR-43", "충북", "chungbuk", 9),
                    new Seed("KR-44", "충남", "chungnam", 10),
                    new Seed("KR-45", "전북", "jeonbuk", 11),
                    new Seed("KR-46", "전남", "jeonnam", 12),
                    new Seed("KR-47", "경북", "gyeongbuk", 13),
                    new Seed("KR-48", "경남", "gyeongnam", 14),
                    new Seed("KR-49", "제주", "jeju", 15),
                    new Seed("KR-50", "세종", "sejong", 16));

    public static final int COUNT = ALL.size();

    public static List<Seed> all() {
        return ALL;
    }

    private KoreaRegions() {}
}
