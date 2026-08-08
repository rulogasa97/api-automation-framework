package com.reservations.generator.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the {@code passengers[N].fieldName} HTML form-encoded naming
 * convention used by {@code fragments/passenger-row.html} back into the
 * ordered {@code List<Map<String,Object>>} shape {@link
 * com.reservations.generator.domain.flow.FlowRegistry#parsePassengers}
 * already accepts — the same relocation strategy design D2 used for JSON
 * binding, now applied to a real HTML form submission (see the Phase 3
 * apply-progress "Deviations" note this replaces).
 *
 * <p>Pure and framework-free: takes the raw {@code
 * HttpServletRequest#getParameterMap()} shape directly, with no Spring
 * dependency, so it is trivially unit-testable.
 */
final class PassengerFormBinding {

    private static final Pattern ROW_FIELD = Pattern.compile("^passengers\\[(\\d+)]\\.(.+)$");

    private PassengerFormBinding() {
    }

    /**
     * @param parameterMap raw request parameters, as returned by {@code
     *                      HttpServletRequest#getParameterMap()}; only keys
     *                      matching {@code passengers[N].fieldName} are
     *                      considered, every other parameter is ignored.
     * @return one map per passenger row, in ascending row-index order
     *         (submission order), each keyed by field name.
     */
    static List<Map<String, Object>> parseRows(Map<String, String[]> parameterMap) {
        TreeMap<Integer, Map<String, Object>> rowsByIndex = new TreeMap<>();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            Matcher matcher = ROW_FIELD.matcher(entry.getKey());
            if (!matcher.matches()) {
                continue;
            }
            int rowIndex = Integer.parseInt(matcher.group(1));
            String fieldName = matcher.group(2);
            String value = entry.getValue().length > 0 ? entry.getValue()[0] : "";
            rowsByIndex.computeIfAbsent(rowIndex, ignored -> new LinkedHashMap<>()).put(fieldName, value);
        }
        return new ArrayList<>(rowsByIndex.values());
    }
}
