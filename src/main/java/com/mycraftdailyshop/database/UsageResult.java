package com.mycraftdailyshop.database;

public final class UsageResult {
    public enum Status { SUCCESS, PERSONAL_LIMIT, SERVER_LIMIT, STALE, ERROR }
    private final Status status;
    private final int personalUsed;
    private final int serverUsed;

    public UsageResult(Status status, int personalUsed, int serverUsed) {
        this.status = status;
        this.personalUsed = personalUsed;
        this.serverUsed = serverUsed;
    }

    public Status getStatus() { return status; }
    public int getPersonalUsed() { return personalUsed; }
    public int getServerUsed() { return serverUsed; }
}
