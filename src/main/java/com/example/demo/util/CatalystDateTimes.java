package com.example.demo.util;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Catalyst Data Store DateTime values use a space between the date and time
 * and a colon before the milliseconds, for example 2023-06-05 15:09:33:881.
 */
public final class CatalystDateTimes {

    private static final DateTimeFormatter CATALYST_WITH_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");

    private static final DateTimeFormatter CATALYST_DOT_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final DateTimeFormatter CATALYST_NO_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CatalystDateTimes() {
    }

    public static LocalDateTime parse(Object value) {

        if (value == null) {
            return null;
        }

        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }

        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }

        if (value instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) value).getTime())
                    .toLocalDateTime();
        }

        String dateValue = value.toString().trim();

        if (dateValue.isEmpty()) {
            return null;
        }

        try {
            return LocalDateTime.parse(dateValue);
        } catch (Exception ignored) {
            // Try an offset date-time.
        }

        try {
            return OffsetDateTime.parse(dateValue).toLocalDateTime();
        } catch (Exception ignored) {
            // Try Catalyst datetime with colon milliseconds.
        }

        try {
            return LocalDateTime.parse(dateValue, CATALYST_WITH_MILLIS);
        } catch (Exception ignored) {
            // Try Catalyst datetime with decimal milliseconds.
        }

        try {
            return LocalDateTime.parse(dateValue, CATALYST_DOT_MILLIS);
        } catch (Exception ignored) {
            // Try Catalyst datetime without milliseconds.
        }

        try {
            return LocalDateTime.parse(dateValue, CATALYST_NO_MILLIS);
        } catch (Exception ignored) {
            // Try a date-only value.
        }

        try {
            return LocalDate.parse(dateValue).atStartOfDay();
        } catch (Exception ignored) {
            // Last attempt: Catalyst colon-millis rewritten as ISO.
        }

        String iso = dateValue.replaceFirst(" ", "T");
        int lastColon = iso.lastIndexOf(':');
        long colonCount = iso.chars().filter(ch -> ch == ':').count();

        if (colonCount == 3 && lastColon > 0) {
            iso = iso.substring(0, lastColon) + "." + iso.substring(lastColon + 1);
        }

        try {
            return LocalDateTime.parse(iso);
        } catch (Exception ignored) {
            return null;
        }
    }
}
