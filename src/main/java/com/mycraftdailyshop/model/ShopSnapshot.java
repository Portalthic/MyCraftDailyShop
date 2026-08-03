package com.mycraftdailyshop.model;

import java.util.Collections;
import java.util.List;

public final class ShopSnapshot {
    private final String cycleId;
    private final String cycleKey;
    private final long expiresAt;
    private final List<Offer> offers;

    public ShopSnapshot(String cycleId, String cycleKey, long expiresAt, List<Offer> offers) {
        this.cycleId = cycleId;
        this.cycleKey = cycleKey;
        this.expiresAt = expiresAt;
        this.offers = Collections.unmodifiableList(offers);
    }

    public String getCycleId() { return cycleId; }
    public String getCycleKey() { return cycleKey; }
    public long getExpiresAt() { return expiresAt; }
    public List<Offer> getOffers() { return offers; }
}
