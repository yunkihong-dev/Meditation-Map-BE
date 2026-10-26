package com.meditationmap.region.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 주소 문자열의 첫 행정구역(시·도) 이름으로 지역 ID({@code KR-*})를 정한다.
 *
 * <p>FE 관리자 화면이 네이버 지오코딩 결과에서 지역을 고를 때 쓰는 이름표({@code naverGeocode.ts REGION_GEO_NAMES})와 같은
 * 표를 쓴다. 오너가 주소를 바꾸면 지역을 서버가 이 규칙으로 다시 계산한다 — 주소·좌표·지역이 따로 놀지 않게(6b8056f 류 버그 방지).
 * 좌표 bbox 로 지역을 추정하지 않는 이유: FE bbox 표가 거칠어(경기 북부·강원 일부가 빠짐) 정상 주소를 거부하게 된다.
 */
public final class AddressRegionResolver {

    private record Names(String regionId, List<String> names) {}

    private static final List<Names> TABLE =
            List.of(
                    new Names("KR-11", List.of("서울특별시", "서울")),
                    new Names("KR-26", List.of("부산광역시", "부산")),
                    new Names("KR-27", List.of("대구광역시", "대구")),
                    new Names("KR-28", List.of("인천광역시", "인천")),
                    new Names("KR-29", List.of("광주광역시", "광주")),
                    new Names("KR-30", List.of("대전광역시", "대전")),
                    new Names("KR-31", List.of("울산광역시", "울산")),
                    new Names("KR-50", List.of("세종특별자치시", "세종")),
                    new Names("KR-41", List.of("경기도", "경기")),
                    new Names("KR-42", List.of("강원특별자치도", "강원도", "강원")),
                    new Names("KR-43", List.of("충청북도", "충북")),
                    new Names("KR-44", List.of("충청남도", "충남")),
                    new Names("KR-45", List.of("전북특별자치도", "전라북도", "전북")),
                    new Names("KR-46", List.of("전라남도", "전남")),
                    new Names("KR-47", List.of("경상북도", "경북")),
                    new Names("KR-48", List.of("경상남도", "경남")),
                    new Names("KR-49", List.of("제주특별자치도", "제주도", "제주")));

    /** 긴 이름부터 비교한다("광주광역시" 가 "광주" 보다 먼저). */
    private static final List<String[]> PREFIXES;

    static {
        List<String[]> all = new ArrayList<>();
        for (Names n : TABLE) {
            for (String name : n.names()) {
                all.add(new String[] {name, n.regionId()});
            }
        }
        all.sort(Comparator.comparingInt((String[] a) -> a[0].length()).reversed());
        PREFIXES = List.copyOf(all);
    }

    private AddressRegionResolver() {}

    /** 주소 첫 토큰이 시·도 이름이면 그 지역. "경기 광주시" 는 경기(KR-41)다(광주광역시 아님). */
    public static Optional<RegionId> resolve(String address) {
        if (address == null) {
            return Optional.empty();
        }
        String a = address.trim();
        int space = a.indexOf(' ');
        String head = space < 0 ? a : a.substring(0, space);
        for (String[] p : PREFIXES) {
            if (head.equals(p[0])) {
                return Optional.of(RegionId.of(p[1]));
            }
        }
        return Optional.empty();
    }
}
