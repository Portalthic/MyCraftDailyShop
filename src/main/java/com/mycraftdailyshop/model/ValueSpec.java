package com.mycraftdailyshop.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

public final class ValueSpec {
    private final boolean normal;
    private final BigDecimal min;
    private final BigDecimal max;
    private final BigDecimal center;
    private final BigDecimal spread;

    private ValueSpec(boolean normal, BigDecimal min, BigDecimal max, BigDecimal center, BigDecimal spread) {
        this.normal = normal;
        this.min = min;
        this.max = max;
        this.center = center;
        this.spread = spread;
    }

    public static ValueSpec parse(String input) {
        if (input == null || input.trim().isEmpty()) throw new IllegalArgumentException("随机表达式不能为空");
        String value = input.trim().replace(" ", "");
        boolean normal = false;
        if (value.regionMatches(true, 0, "U:", 0, 2)) value = value.substring(2);
        else if (value.regionMatches(true, 0, "N:", 0, 2)) { normal = true; value = value.substring(2); }
        if (value.contains(",")) {
            String[] parts = value.split(",", -1);
            if (parts.length != 2) throw new IllegalArgumentException("中心值表达式格式错误: " + input);
            BigDecimal center = decimal(parts[0], input);
            BigDecimal spread = decimal(parts[1], input);
            if (spread.signum() < 0) throw new IllegalArgumentException("扩展区间不能小于 0: " + input);
            return new ValueSpec(normal, center.subtract(spread), center.add(spread), center, spread);
        }
        int dash = value.indexOf('-', 1);
        if (dash > 0) {
            BigDecimal min = decimal(value.substring(0, dash), input);
            BigDecimal max = decimal(value.substring(dash + 1), input);
            if (min.compareTo(max) > 0) throw new IllegalArgumentException("最小值不能大于最大值: " + input);
            BigDecimal center = min.add(max).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
            return new ValueSpec(normal, min, max, center, max.subtract(min).divide(BigDecimal.valueOf(6), 8, RoundingMode.HALF_UP));
        }
        BigDecimal fixed = decimal(value, input);
        return new ValueSpec(false, fixed, fixed, fixed, BigDecimal.ZERO);
    }

    private static BigDecimal decimal(String value, String source) {
        try { return new BigDecimal(value); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("数值格式错误: " + source); }
    }

    public BigDecimal sampleMoney(Random random) {
        BigDecimal result = sample(random);
        if (result.compareTo(new BigDecimal("0.01")) < 0) result = new BigDecimal("0.01");
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    public int sampleInteger(Random random, boolean allowUnlimited) {
        if (allowUnlimited && min.compareTo(BigDecimal.valueOf(-1)) == 0 && max.compareTo(BigDecimal.valueOf(-1)) == 0) return -1;
        int result = sample(random).setScale(0, RoundingMode.HALF_UP).intValue();
        if (result < 0) throw new IllegalArgumentException("随机结果不能为负数");
        return result;
    }

    private BigDecimal sample(Random random) {
        if (min.compareTo(max) == 0) return min;
        double result;
        if (normal) {
            double sigma = spread.signum() == 0 ? max.subtract(min).doubleValue() / 6D : spread.doubleValue();
            result = center.doubleValue() + random.nextGaussian() * sigma;
            result = Math.max(min.doubleValue(), Math.min(max.doubleValue(), result));
        } else {
            result = min.doubleValue() + random.nextDouble() * max.subtract(min).doubleValue();
        }
        return BigDecimal.valueOf(result);
    }

    public BigDecimal getMin() { return min; }
    public BigDecimal getMax() { return max; }
    public String describeInteger() {
        if (min.compareTo(max) == 0) return min.stripTrailingZeros().toPlainString();
        return min.setScale(0, RoundingMode.HALF_UP) + "-" + max.setScale(0, RoundingMode.HALF_UP);
    }
}
