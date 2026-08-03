package com.mycraftdailyshop.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

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
}
