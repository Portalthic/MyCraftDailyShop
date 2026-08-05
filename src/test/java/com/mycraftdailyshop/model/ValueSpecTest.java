package com.mycraftdailyshop.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ValueSpecTest {
    @Test void parsesFixedAndUnlimited() {
        assertEquals(new BigDecimal("200"), ValueSpec.parse("200").getMin());
        assertEquals(-1, ValueSpec.parse("-1").sampleInteger(new Random(1), true));
    }

    @Test void samplesUniformRangeInsideBounds() {
        ValueSpec spec = ValueSpec.parse("U:200-800");
        Random random = new Random(7);
        for (int i = 0; i < 1000; i++) {
            BigDecimal value = spec.sampleMoney(random);
            assertTrue(value.compareTo(new BigDecimal("200")) >= 0);
            assertTrue(value.compareTo(new BigDecimal("800")) <= 0);
        }
    }

    @Test void parsesCenterAndSpread() {
        ValueSpec spec = ValueSpec.parse("400,100");
        assertEquals(new BigDecimal("300"), spec.getMin());
        assertEquals(new BigDecimal("500"), spec.getMax());
    }

    @Test void rejectsReversedRange() {
        assertThrows(IllegalArgumentException.class, () -> ValueSpec.parse("800-200"));
    }

    @Test void samplesHalfNormalExpressionsInsideBounds() {
        ValueSpec left = ValueSpec.parse("NL:300-500");
        ValueSpec right = ValueSpec.parse("NR:300-500");
        Random random = new Random(11);
        BigDecimal leftTotal = BigDecimal.ZERO, rightTotal = BigDecimal.ZERO;
        for (int i = 0; i < 2000; i++) {
            BigDecimal l = left.sampleDecimal(random), r = right.sampleDecimal(random);
            assertTrue(l.compareTo(new BigDecimal("300")) >= 0 && l.compareTo(new BigDecimal("500")) <= 0);
            assertTrue(r.compareTo(new BigDecimal("300")) >= 0 && r.compareTo(new BigDecimal("500")) <= 0);
            leftTotal = leftTotal.add(l); rightTotal = rightTotal.add(r);
        }
        assertTrue(leftTotal.compareTo(rightTotal) > 0);
    }
}
