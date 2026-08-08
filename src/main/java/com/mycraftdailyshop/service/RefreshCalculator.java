package com.mycraftdailyshop.service;

import com.mycraftdailyshop.model.ShopConfig;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

public final class RefreshCalculator {
    private final ZoneId zone;

    public RefreshCalculator(ZoneId zone) {
        this.zone = zone;
    }

    public RefreshCycle current(ShopConfig shop, long nowMillis) {
        if (shop.getRefreshType().trim().equalsIgnoreCase("timely")) {
            long interval = shop.getRefreshIntervalMillis();
            if (interval <= 0L) throw new IllegalArgumentException("timely 刷新间隔必须大于 0");
            long anchor = LocalDate.of(1970, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli();
            long cycleIndex = Math.floorDiv(Math.subtractExact(nowMillis, anchor), interval);
            long start = Math.addExact(anchor, Math.multiplyExact(cycleIndex, interval));
            return new RefreshCycle(start, Math.addExact(start, interval));
        }
        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(zone);
        ZonedDateTime cursor = now.truncatedTo(ChronoUnit.DAYS).with(shop.getRefreshTime());
        for (int i = 0; i < 370; i++, cursor = cursor.minusDays(1)) {
            if (!cursor.isAfter(now) && matches(shop.getRefreshType(), cursor.toLocalDate())) {
                ZonedDateTime next = findNext(shop, cursor.plusDays(1));
                return new RefreshCycle(cursor.toInstant().toEpochMilli(), next.toInstant().toEpochMilli());
            }
        }
        throw new IllegalStateException("无法计算商店 " + shop.getId() + " 的刷新周期");
    }

    public boolean matches(String expression, LocalDate date) {
        String value = expression.toLowerCase().replace(" ", "");
        if (value.equals("daily")) return true;
        if (value.startsWith("weekly:")) return values(value.substring(7), 1, 7).contains(date.getDayOfWeek().getValue());
        if (value.startsWith("monthly:")) return values(value.substring(8), 1, 31).contains(date.getDayOfMonth());
        throw new IllegalArgumentException("不支持的刷新类型: " + expression);
    }

    public void validate(String expression) {
        String value = expression.toLowerCase().replace(" ", "");
        if (value.equals("timely")) return;
        if (value.equals("daily")) return;
        if (value.startsWith("weekly:")) { values(value.substring(7), 1, 7); return; }
        if (value.startsWith("monthly:")) { values(value.substring(8), 1, 31); return; }
        throw new IllegalArgumentException("不支持的刷新类型: " + expression);
    }

    public long parseTimelyInterval(String input) {
        if (input == null) throw new IllegalArgumentException("timely 刷新间隔不能为空");
        String[] parts = input.trim().split(":", -1);
        if (parts.length != 3) throw new IllegalArgumentException("timely 刷新间隔必须使用 H:mm:ss 格式: " + input);
        long hours;
        int minutes;
        int seconds;
        try {
            hours = Long.parseLong(parts[0]);
            minutes = Integer.parseInt(parts[1]);
            seconds = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("timely 刷新间隔必须使用整数: " + input);
        }
        if (hours < 0 || minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59) {
            throw new IllegalArgumentException("timely 刷新间隔数值无效: " + input);
        }
        try {
            long totalSeconds = Math.addExact(Math.addExact(Math.multiplyExact(hours, 3600L), minutes * 60L), seconds);
            if (totalSeconds <= 0L) throw new IllegalArgumentException("timely 刷新间隔必须大于 0");
            return Math.multiplyExact(totalSeconds, 1000L);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("timely 刷新间隔过大: " + input);
        }
    }

    private ZonedDateTime findNext(ShopConfig shop, ZonedDateTime start) {
        ZonedDateTime cursor = start.truncatedTo(ChronoUnit.DAYS).with(shop.getRefreshTime());
        for (int i = 0; i < 370; i++, cursor = cursor.plusDays(1)) {
            if (matches(shop.getRefreshType(), cursor.toLocalDate())) return cursor;
        }
        throw new IllegalStateException("无法计算下一次刷新时间");
    }

    private Set<Integer> values(String input, int min, int max) {
        Set<Integer> result = new HashSet<>();
        if (input.isEmpty()) throw new IllegalArgumentException("刷新日期列表不能为空");
        for (String part : input.split(",")) {
            int value;
            try { value = Integer.parseInt(part); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("刷新日期必须为整数: " + part); }
            if (value < min || value > max) throw new IllegalArgumentException("刷新日期必须在 " + min + " 到 " + max + " 之间: " + value);
            result.add(value);
        }
        return result;
    }
}
