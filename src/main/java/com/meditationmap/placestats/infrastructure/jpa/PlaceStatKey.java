package com.meditationmap.placestats.infrastructure.jpa;

import java.io.Serializable;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** (place_id, stat_date) 복합 키. stat_date 는 KST 날짜. */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PlaceStatKey implements Serializable {
    private String placeId;
    private LocalDate statDate;
}
