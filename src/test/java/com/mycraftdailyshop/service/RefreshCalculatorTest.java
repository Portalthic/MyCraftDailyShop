package com.mycraftdailyshop.service;

import com.mycraftdailyshop.model.ShopConfig;
import com.mycraftdailyshop.model.ShopScene;
import com.mycraftdailyshop.model.ShopType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class RefreshCalculatorTest {
    private final RefreshCalculator calculator = new RefreshCalculator(ZoneId.of("Asia/Shanghai"));

    @Test void matchesWeeklyDays() {
        assertTrue(calculator.matches("weekly:1,5", LocalDate.of(2026, 8, 3)));
        assertFalse(calculator.matches("weekly:1,5", LocalDate.of(2026, 8, 4)));
    }

    @Test void monthlyMissingDateDoesNotMatch() {
        assertTrue(calculator.matches("monthly:30", LocalDate.of(2026, 4, 30)));
        assertFalse(calculator.matches("monthly:30", LocalDate.of(2026, 2, 28)));
    }

    @Test void validatesRanges() {
        assertThrows(IllegalArgumentException.class, () -> calculator.validate("weekly:0"));
        assertThrows(IllegalArgumentException.class, () -> calculator.validate("monthly:32"));
    }

    @Test void parsesTimelyIntervals() {
        assertEquals(7_200_000L, calculator.parseTimelyInterval("2:00:00"));
        assertEquals(1_800_000L, calculator.parseTimelyInterval("0:30:00"));
        assertEquals(90_061_000L, calculator.parseTimelyInterval("25:01:01"));
        assertThrows(IllegalArgumentException.class, () -> calculator.parseTimelyInterval("0:00:00"));
        assertThrows(IllegalArgumentException.class, () -> calculator.parseTimelyInterval("1:60:00"));
        assertThrows(IllegalArgumentException.class, () -> calculator.parseTimelyInterval("01:30"));
    }

    @Test void timelyCyclesAreAnchoredInConfiguredTimezone() {
        long anchor = LocalDate.of(1970, 1, 1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        ShopConfig shop = new ShopConfig("timely-test", ShopType.SELL, ShopScene.PERSONAL,
                "timely", LocalTime.MIDNIGHT, 25_200_000L, null, null, null, null, null,
                "test", Collections.emptyList(), Collections.emptyMap(), Collections.emptyList());
        RefreshCycle cycle = calculator.current(shop, anchor + 28_800_123L);
        assertEquals(anchor + 25_200_000L, cycle.getStart());
        assertEquals(anchor + 50_400_000L, cycle.getEnd());
        assertEquals(Long.toString(anchor + 25_200_000L), cycle.getKey());
    }
}
