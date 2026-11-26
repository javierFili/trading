package com.cryptotrading.strategies.domain;

import com.cryptotrading.indicators.domain.Atr;
import com.cryptotrading.indicators.domain.Ema;
import com.cryptotrading.indicators.domain.IndicatorSeries;
import com.cryptotrading.indicators.domain.Macd;
import com.cryptotrading.indicators.domain.Rsi;
import com.cryptotrading.indicators.domain.VolumeSma;
import com.cryptotrading.market.domain.CandleSeries;
import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.DecimalPolicy;
import com.cryptotrading.shared.domain.Timeframe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Experimental trend-following strategy (NOT assumed profitable). Evaluated on the last closed candle.
 *
 * <p>ENTRY_LONG when ALL of: EMA(fast) &gt; EMA(slow) (uptrend); MACD histogram crosses above zero on the last
 * candle (momentum trigger); rsiMin ≤ RSI ≤ rsiMax (momentum without overbought). Volume above
 * volumeFactor × its SMA is a confirmation that raises the score but is not required.
 * Suggested stop = close − stopAtr × ATR; take profit = close + tpAtr × ATR.
 *
 * <p>EXIT_LONG when EMA(fast) &lt; EMA(slow) (trend broken) or the MACD histogram crosses below zero.
 *
 * <p>Score (heuristic, not a probability): trend 40 + MACD cross 30 + RSI in range 20 + volume 10.
 */
public final class EmaRsiMacdStrategy implements Strategy {

    public static final String CODE = "EMA_RSI_MACD";
    static final String LOGIC_VERSION = "1.0.0";

    private final EmaRsiMacdParameters p;
    private final StrategyDescriptor descriptor;

    public EmaRsiMacdStrategy(EmaRsiMacdParameters parameters, Timeframe timeframe) {
        this.p = parameters;
        Map<String, String> params = parameters.asMap();
        this.descriptor = new StrategyDescriptor(CODE, StrategyVersions.of(LOGIC_VERSION, params),
                "EMA " + p.emaFast() + "/" + p.emaSlow() + " + RSI " + p.rsiPeriod() + " + MACD + ATR stop/target",
                timeframe, params);
    }

    @Override
    public StrategyDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public int requiredHistory() {
        return p.lookback();
    }

    @Override
    public StrategyEvaluation evaluate(CandleSeries series) {
        if (series.isEmpty()) {
            throw new IllegalArgumentException("Empty series");
        }
        Candle last = series.last();
        if (series.size() < requiredHistory()) {
            return noAction(last, Map.of(), List.of("Historial insuficiente: " + series.size() + " < " + requiredHistory() + " velas"));
        }
        IndicatorSeries<BigDecimal> emaFast = new Ema(p.emaFast()).calculate(series);
        IndicatorSeries<BigDecimal> emaSlow = new Ema(p.emaSlow()).calculate(series);
        IndicatorSeries<BigDecimal> rsi = new Rsi(p.rsiPeriod()).calculate(series);
        IndicatorSeries<Macd.Value> macd = new Macd(p.macdFast(), p.macdSlow(), p.macdSignal()).calculate(series);
        IndicatorSeries<BigDecimal> atr = new Atr(p.atrPeriod()).calculate(series);
        IndicatorSeries<BigDecimal> volSma = new VolumeSma(p.volumePeriod()).calculate(series);

        BigDecimal fast = emaFast.latest().value();
        BigDecimal slow = emaSlow.latest().value();
        BigDecimal rsiNow = rsi.latest().value();
        BigDecimal histNow = macd.latest().value().histogram();
        BigDecimal histPrev = macd.fromEnd(1).value().histogram();
        BigDecimal atrNow = atr.latest().value();
        BigDecimal volumeAvg = volSma.latest().value();

        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put(emaFast.indicatorName(), scale(fast));
        values.put(emaSlow.indicatorName(), scale(slow));
        values.put(rsi.indicatorName(), scale(rsiNow));
        values.put("MACD", scale(macd.latest().value().macd()));
        values.put("MACD_SIGNAL", scale(macd.latest().value().signal()));
        values.put("MACD_HIST", scale(histNow));
        values.put(atr.indicatorName(), scale(atrNow));
        values.put("VOLUME", last.volume());
        values.put(volSma.indicatorName(), scale(volumeAvg));

        boolean trendUp = fast.compareTo(slow) > 0;
        boolean macdCrossUp = histNow.signum() > 0 && histPrev.signum() <= 0;
        boolean macdCrossDown = histNow.signum() < 0 && histPrev.signum() >= 0;
        boolean rsiInRange = rsiNow.compareTo(p.rsiMin()) >= 0 && rsiNow.compareTo(p.rsiMax()) <= 0;
        boolean volumeOk = last.volume().compareTo(volumeAvg.multiply(p.volumeFactor())) >= 0;

        List<String> reasons = new ArrayList<>();
        reasons.add((trendUp ? "✔" : "✘") + " Tendencia: EMA" + p.emaFast() + " " + (trendUp ? ">" : "≤") + " EMA" + p.emaSlow());
        reasons.add((macdCrossUp ? "✔" : "✘") + " MACD: histograma " + (macdCrossUp ? "cruza por encima de 0" : "sin cruce alcista") + " (" + scale(histPrev).toPlainString() + " → " + scale(histNow).toPlainString() + ")");
        reasons.add((rsiInRange ? "✔" : "✘") + " RSI " + scale(rsiNow).toPlainString() + " en [" + p.rsiMin().toPlainString() + ", " + p.rsiMax().toPlainString() + "]");
        reasons.add((volumeOk ? "✔" : "·") + " Volumen " + last.volume().toPlainString() + " vs media " + scale(volumeAvg).toPlainString() + " (confirmación opcional)");

        int score = (trendUp ? 40 : 0) + (macdCrossUp ? 30 : 0) + (rsiInRange ? 20 : 0) + (volumeOk ? 10 : 0);

        if (trendUp && macdCrossUp && rsiInRange) {
            BigDecimal stop = last.close().subtract(atrNow.multiply(p.stopAtrMultiplier(), DecimalPolicy.CALCULATION));
            BigDecimal target = last.close().add(atrNow.multiply(p.takeProfitAtrMultiplier(), DecimalPolicy.CALCULATION));
            if (stop.signum() <= 0) {
                return noAction(last, values, List.of("Stop calculado no positivo: volatilidad excesiva"));
            }
            List<String> invalidation = List.of(
                    "El precio cae por debajo del stop sugerido " + scale(stop).toPlainString(),
                    "EMA" + p.emaFast() + " cruza por debajo de EMA" + p.emaSlow(),
                    "El histograma MACD vuelve a ser negativo antes de la ejecución",
                    "La propuesta expira sin aprobación");
            return new StrategyEvaluation(SignalType.ENTRY_LONG, last.openTime(), last.close(),
                    new ConfidenceScore(BigDecimal.valueOf(score)), values, reasons, invalidation, scale(stop), scale(target));
        }
        if (!trendUp || macdCrossDown) {
            List<String> exitReasons = new ArrayList<>(reasons);
            exitReasons.add(!trendUp ? "Salida: tendencia rota (EMA rápida ≤ EMA lenta)" : "Salida: histograma MACD cruza por debajo de 0");
            int exitScore = (!trendUp ? 60 : 0) + (macdCrossDown ? 40 : 0);
            return new StrategyEvaluation(SignalType.EXIT_LONG, last.openTime(), last.close(),
                    new ConfidenceScore(BigDecimal.valueOf(exitScore)), values, exitReasons,
                    List.of("Solo aplica si existe una posición abierta"), null, null);
        }
        return new StrategyEvaluation(SignalType.NO_ACTION, last.openTime(), last.close(),
                new ConfidenceScore(BigDecimal.valueOf(score)), values, reasons, List.of(), null, null);
    }

    private static StrategyEvaluation noAction(Candle last, Map<String, BigDecimal> values, List<String> reasons) {
        return new StrategyEvaluation(SignalType.NO_ACTION, last.openTime(), last.close(),
                new ConfidenceScore(BigDecimal.ZERO), values, reasons, List.of(), null, null);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_EVEN);
    }
}
