package com.trekking.app.features.dashboard.enums;

public enum TrendGranularity {
    WEEK(12, 104),
    MONTH(12, 60),
    QUARTER(8, 40),
    YEAR(5, 20);

    private final int defaultPeriods;
    private final int maxPeriods;

    TrendGranularity(int defaultPeriods, int maxPeriods) {
        this.defaultPeriods = defaultPeriods;
        this.maxPeriods = maxPeriods;
    }

    public int defaultPeriods() {
        return defaultPeriods;
    }

    public int maxPeriods() {
        return maxPeriods;
    }
}
