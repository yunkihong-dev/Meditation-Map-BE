package com.meditationmap.place.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.util.StringUtils;

/** places.data JSON 내 programs[] — 기간 기반 상태 정규화 */
public final class PlaceProgramNormalizer {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private PlaceProgramNormalizer() {}

    public static ObjectNode normalizePlaceData(ObjectNode data) {
        if (data == null) {
            return data;
        }
        JsonNode programs = data.get("programs");
        if (programs == null || !programs.isArray()) {
            return data;
        }
        ArrayNode normalized = data.arrayNode();
        for (JsonNode node : programs) {
            if (node instanceof ObjectNode objectNode) {
                normalized.add(normalizeProgram(objectNode.deepCopy()));
            } else {
                normalized.add(node);
            }
        }
        data.set("programs", normalized);
        return data;
    }

    private static ObjectNode normalizeProgram(ObjectNode program) {
        String kind = textOrDefault(program, "kind", "program");
        String startDate = normalizeDateField(program, "startDate");
        String endDate = normalizeDateField(program, "endDate");
        String legacyStatus = program.path("status").asText(null);

        if (startDate != null) {
            program.put("startDate", startDate);
        } else {
            program.remove("startDate");
        }
        if (endDate != null) {
            program.put("endDate", endDate);
        } else {
            program.remove("endDate");
        }

        program.put("status", computeStatus(kind, startDate, endDate, legacyStatus));
        return program;
    }

    static String computeStatus(String kind, String startDate, String endDate, String legacyStatus) {
        LocalDate today = LocalDate.now(KST);
        if ("event".equals(kind)) {
            if (startDate != null && endDate != null) {
                LocalDate end = LocalDate.parse(endDate, ISO_DATE);
                return today.isAfter(end) ? "past" : "ongoing";
            }
            return "past".equals(legacyStatus) ? "past" : "ongoing";
        }
        if (endDate != null) {
            LocalDate end = LocalDate.parse(endDate, ISO_DATE);
            if (today.isAfter(end)) {
                return "past";
            }
        }
        return "ongoing";
    }

    private static String normalizeDateField(ObjectNode program, String field) {
        String raw = textOrNull(program, field);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), ISO_DATE).format(ISO_DATE);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String textOrNull(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private static String textOrDefault(ObjectNode node, String field, String fallback) {
        String text = textOrNull(node, field);
        return text != null ? text : fallback;
    }
}
