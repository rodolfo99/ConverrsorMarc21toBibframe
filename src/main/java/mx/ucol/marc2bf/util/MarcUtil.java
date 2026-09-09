package mx.ucol.marc2bf.util;

import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;
import org.marc4j.marc.VariableField;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Convenience functions for Marc4J records. */
public final class MarcUtil {
    private MarcUtil() {}

    public static String control(Record record, String tag) {
        VariableField field = record.getVariableField(tag);
        return field instanceof ControlField cf ? safe(cf.getData()) : "";
    }

    public static DataField firstDataField(Record record, String tag) {
        VariableField field = record.getVariableField(tag);
        return field instanceof DataField df ? df : null;
    }

    public static List<DataField> dataFields(Record record, String tag) {
        List<DataField> result = new ArrayList<>();
        for (VariableField field : record.getVariableFields(tag)) {
            if (field instanceof DataField df) {
                result.add(df);
            }
        }
        return result;
    }

    public static List<DataField> dataFields(Record record, Set<String> tags) {
        List<DataField> result = new ArrayList<>();
        for (VariableField field : record.getVariableFields()) {
            if (field instanceof DataField df && tags.contains(df.getTag())) {
                result.add(df);
            }
        }
        return result;
    }

    public static String subfield(DataField field, char code) {
        if (field == null) return "";
        Subfield subfield = field.getSubfield(code);
        return subfield == null ? "" : safe(subfield.getData());
    }

    public static List<String> subfields(DataField field, char code) {
        if (field == null) return List.of();
        return field.getSubfields(code).stream()
                .map(Subfield::getData)
                .map(MarcUtil::safe)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public static String join(DataField field, char... codes) {
        List<String> values = new ArrayList<>();
        for (char code : codes) {
            values.addAll(subfields(field, code));
        }
        return values.stream().map(MarcUtil::cleanPunctuation)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    public static String joinAllSubfields(DataField field, Set<Character> excluded) {
        if (field == null) return "";
        List<String> values = new ArrayList<>();
        for (Subfield sf : field.getSubfields()) {
            if (!excluded.contains(sf.getCode())) {
                String value = safe(sf.getData());
                if (!value.isBlank()) values.add(value);
            }
        }
        return String.join(" ", values).trim();
    }

    public static String display(DataField field) {
        if (field == null) return "";
        StringBuilder value = new StringBuilder(field.getTag())
                .append(' ').append(field.getIndicator1())
                .append(field.getIndicator2());
        for (Subfield sf : field.getSubfields()) {
            value.append(" $").append(sf.getCode()).append(' ').append(safe(sf.getData()));
        }
        return value.toString();
    }

    public static String cleanPunctuation(String value) {
        String result = safe(value);
        while (!result.isEmpty() && ".,;:/ ".indexOf(result.charAt(result.length() - 1)) >= 0) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    public static String normalizeCode(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static String normalizeIsbn(String value) {
        String withoutQualifier = safe(value).replaceAll("\\([^)]*\\)", "");
        return withoutQualifier.replaceAll("[^0-9Xx]", "").toUpperCase(Locale.ROOT);
    }

    public static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static List<String> distinct(List<String> values) {
        Set<String> set = new LinkedHashSet<>();
        for (String value : values) {
            String cleaned = safe(value);
            if (!cleaned.isBlank()) set.add(cleaned);
        }
        return new ArrayList<>(set);
    }

    public static String year(String value) {
        var matcher = java.util.regex.Pattern.compile("(?:^|\\D)(\\d{4})(?:\\D|$)").matcher(safe(value));
        return matcher.find() ? matcher.group(1) : "";
    }
}
