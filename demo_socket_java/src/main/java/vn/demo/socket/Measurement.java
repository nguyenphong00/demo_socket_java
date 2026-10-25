package vn.demo.socket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;

public record Measurement(String clientId, String metric, double value, Instant timestamp) {
    public static Measurement parse(String json, ObjectMapper mapper) throws Exception {
        JsonNode root = mapper.readTree(json);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("JSON phải là object");
        }
        String clientId = requiredText(root, "clientId", 64);
        String metric = requiredText(root, "metric", 64);
        JsonNode valueNode = root.get("value");
        if (valueNode == null || !valueNode.isNumber() || !Double.isFinite(valueNode.doubleValue())) {
            throw new IllegalArgumentException("value phải là số hữu hạn");
        }
        String timestampText = requiredText(root, "timestamp", 40);
        try {
            return new Measurement(clientId, metric, valueNode.doubleValue(), Instant.parse(timestampText));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("timestamp phải theo ISO-8601 UTC, ví dụ 2026-10-25T08:00:00Z");
        }
    }

    private static String requiredText(JsonNode root, String field, int maxLength) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual()) {
            throw new IllegalArgumentException(field + " phải là chuỗi");
        }
        String value = node.textValue().trim();
        if (value.isEmpty() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " phải dài từ 1 đến " + maxLength + " ký tự");
        }
        return value;
    }
}
