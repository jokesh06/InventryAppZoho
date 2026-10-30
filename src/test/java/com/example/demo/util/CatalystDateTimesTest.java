package com.example.demo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class CatalystDateTimesTest {

    @Test
    void parsesCatalystDatetimeWithColonMilliseconds() {
        LocalDateTime parsed = CatalystDateTimes.parse(
                "2023-06-05 15:09:33:881");

        assertEquals(LocalDateTime.of(2023, 6, 5, 15, 9, 33, 881_000_000), parsed);
    }

    @Test
    void parsesCatalystDatetimeWithoutMilliseconds() {
        LocalDateTime parsed = CatalystDateTimes.parse(
                "2023-06-05 15:09:33");

        assertEquals(LocalDateTime.of(2023, 6, 5, 15, 9, 33), parsed);
    }

    @Test
    void returnsNullForEmptyValue() {
        assertNull(CatalystDateTimes.parse(null));
        assertNull(CatalystDateTimes.parse("  "));
        assertNull(CatalystDateTimes.parse("not-a-date"));
    }
}
