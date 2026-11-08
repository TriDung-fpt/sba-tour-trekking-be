package com.trekking.app.features.dashboard.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DashboardMath {
    public static final String UP = "UP";
    public static final String DOWN = "DOWN";
    public static final String FLAT = "FLAT";
    public static final String NEW = "NEW";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private DashboardMath() {
    }

    public static Double changePercent(long current, long previous) {
        if (previous == 0) {
            return null;
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static String trend(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? NEW : FLAT;
        }
        if (current > previous) {
            return UP;
        }
        if (current < previous) {
            return DOWN;
        }
        return FLAT;
    }

    public static Double changePercent(Double current, Double previous) {
        if (previous == null || previous == 0d) {
            return null;
        }
        double value = current == null ? 0d : current;
        return BigDecimal.valueOf(value)
                .subtract(BigDecimal.valueOf(previous))
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static String trend(Double current, Double previous) {
        if (previous == null || previous == 0d) {
            return current != null && current > 0d ? NEW : FLAT;
        }
        double value = current == null ? 0d : current;
        int compared = BigDecimal.valueOf(value).compareTo(BigDecimal.valueOf(previous));
        if (compared > 0) {
            return UP;
        }
        if (compared < 0) {
            return DOWN;
        }
        return FLAT;
    }

    public static Double ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static String fillLevel(int capacity, long bookedPassengers, Double fillRate) {
        if (capacity > 0 && bookedPassengers >= capacity) {
            return "FULL";
        }
        if (fillRate == null) {
            return "LOW";
        }
        if (fillRate >= 100d) {
            return "FULL";
        }
        if (fillRate >= 90d) {
            return "HIGH";
        }
        if (fillRate < 40d) {
            return "LOW";
        }
        return "NORMAL";
    }
}
