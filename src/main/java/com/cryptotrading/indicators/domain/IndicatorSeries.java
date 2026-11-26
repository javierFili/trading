package com.cryptotrading.indicators.domain;

import java.util.List;
import java.util.Objects;

/** Output of an indicator, ascending by time. Contains values only once the warm-up period is complete. */
public record IndicatorSeries<V>(String indicatorName, List<IndicatorPoint<V>> points) {

    public IndicatorSeries {
        Objects.requireNonNull(indicatorName, "indicatorName");
        points = List.copyOf(points);
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    /** @param offsetFromEnd 0 = latest value, 1 = previous, ... */
    public IndicatorPoint<V> fromEnd(int offsetFromEnd) {
        if (offsetFromEnd < 0 || offsetFromEnd >= points.size()) {
            throw new IndexOutOfBoundsException("No value at offset " + offsetFromEnd + " from end");
        }
        return points.get(points.size() - 1 - offsetFromEnd);
    }

    public IndicatorPoint<V> latest() {
        return fromEnd(0);
    }
}
