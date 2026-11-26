package com.cryptotrading.strategies.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explainable result of evaluating a strategy on the last closed candle.
 *
 * @param candleOpenTime          the candle the evaluation refers to (deduplication key component)
 * @param referencePrice          close price of that candle
 * @param indicatorValues         indicator snapshot used for the decision, e.g. {@code "EMA(20)" -> 64123.5}
 * @param reasons                 human-readable conditions that were met / not met
 * @param invalidationConditions  conditions under which the signal no longer holds
 * @param suggestedStopLoss       optional technical stop (e.g. ATR based); risk module has the final word
 * @param suggestedTakeProfit     optional
 */
public record StrategyEvaluation(
        SignalType type,
        Instant candleOpenTime,
        BigDecimal referencePrice,
        ConfidenceScore score,
        Map<String, BigDecimal> indicatorValues,
        List<String> reasons,
        List<String> invalidationConditions,
        BigDecimal suggestedStopLoss,
        BigDecimal suggestedTakeProfit) {

    public StrategyEvaluation {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(candleOpenTime, "candleOpenTime");
        Objects.requireNonNull(referencePrice, "referencePrice");
        Objects.requireNonNull(score, "score");
        indicatorValues = Map.copyOf(indicatorValues);
        reasons = List.copyOf(reasons);
        invalidationConditions = List.copyOf(invalidationConditions);
    }
}
