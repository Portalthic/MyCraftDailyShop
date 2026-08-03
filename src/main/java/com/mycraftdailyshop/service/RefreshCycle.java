package com.mycraftdailyshop.service;

public final class RefreshCycle {
    private final long start;
    private final long end;

    public RefreshCycle(long start, long end) {
        this.start = start;
        this.end = end;
    }

    public long getStart() { return start; }
    public long getEnd() { return end; }
    public String getKey() { return Long.toString(start); }
}
