package com.cryptotrading.indicators.domain;

import com.cryptotrading.market.domain.CandleSeries;
import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Timeframe;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.cryptotrading.shared.domain.CandleFixtures.BTC_USDT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Expected values come from an independent Decimal(prec=50) Python implementation of the textbook formulas
 * over the same deterministic series (see docs/BACKTESTING.md, "Validación de indicadores").
 */
class IndicatorsTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("1E-25");
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    static final CandleSeries SERIES = series(80);

    /** close_i = 100 + (37i mod 23) − (11i mod 7) + 0.5i ; high = close + (13i mod 5) + 1 ; low = close − (7i mod 4) − 1. */
    static CandleSeries series(int n) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BigDecimal close = BigDecimal.valueOf(100 + (i * 37L) % 23 - (i * 11L) % 7).add(new BigDecimal("0.5").multiply(BigDecimal.valueOf(i)));
            BigDecimal high = close.add(BigDecimal.valueOf((i * 13L) % 5 + 1));
            BigDecimal low = close.subtract(BigDecimal.valueOf((i * 7L) % 4 + 1));
            Instant open = T0.plus(Timeframe.H1.duration().multipliedBy(i));
            candles.add(new Candle(BTC_USDT, Timeframe.H1, open, open.plus(Timeframe.H1.duration()).minusMillis(1),
                    close, high, low, close, BigDecimal.valueOf(10 + i % 5), BigDecimal.TEN, 1));
        }
        return CandleSeries.of(BTC_USDT, Timeframe.H1, T0.plus(Timeframe.H1.duration().multipliedBy(n)), candles);
    }

    @Test
    void emaMatchesReference() {
        var ema20 = new Ema(20).calculate(SERIES);
        var ema50 = new Ema(50).calculate(SERIES);
        assertClose(ema20.latest().value(), "142.14208300594526950500137474559819785497284251942");
        assertClose(ema50.latest().value(), "134.92216720415303353385751562021968147260000383713");
        assertThat(ema20.points()).hasSize(80 - 19);
        assertThat(ema50.points().getFirst().candleOpenTime()).isEqualTo(SERIES.candles().get(49).openTime());
    }

    @Test
    void rsiMatchesReference() {
        var rsi = new Rsi(14).calculate(SERIES);
        assertClose(rsi.latest().value(), "50.011775195491854813944619955121858803596559338294");
        assertThat(rsi.points().getFirst().candleOpenTime()).isEqualTo(SERIES.candles().get(14).openTime());
    }

    @Test
    void atrMatchesReference() {
        assertClose(new Atr(14).calculate(SERIES).latest().value(), "13.051389694135381426622222336067159072625303214966");
    }

    @Test
    void macdMatchesReference() {
        var macd = new Macd(12, 26, 9).calculate(SERIES);
        Macd.Value v = macd.latest().value();
        assertClose(v.macd(), "3.10868621239645830378521282301841422692262355954");
        assertClose(v.signal(), "3.3103119260696814588862458431437139743233050940698");
        assertClose(v.histogram(), "-0.2016257136732231551010330201252997474006815345298");
        assertThat(macd.points().getFirst().candleOpenTime()).isEqualTo(SERIES.candles().get(33).openTime());
    }

    @Test
    void bollingerMatchesReference() {
        BollingerBands.Value v = new BollingerBands(20, BigDecimal.valueOf(2)).calculate(SERIES).latest().value();
        assertClose(v.lower(), "127.06385875623358498133041335907371198289450339550");
        assertClose(v.middle(), "142.3");
        assertClose(v.upper(), "157.53614124376641501866958664092628801710549660450");
    }

    @Test
    void emaOfConstantSeriesIsTheConstantAndHandCheckedSmallCase() {
        // closes 1..5, EMA(3): seed SMA(1,2,3)=2, k=0.5 -> 3, 4
        CandleSeries s = linear(5);
        var ema = new Ema(3).calculate(s);
        assertThat(ema.points()).extracting(p -> p.value().stripTrailingZeros().toPlainString())
                .containsExactly("2", "3", "4");
    }

    @Test
    void rsiOfStrictlyRisingSeriesIs100() {
        assertThat(new Rsi(3).calculate(linear(10)).latest().value()).isEqualByComparingTo("100");
    }

    @Test
    void notEnoughDataProducesNoValues() {
        assertThat(new Ema(20).calculate(series(10)).isEmpty()).isTrue();
        assertThat(new Macd(12, 26, 9).calculate(series(33)).isEmpty()).isTrue();
        assertThat(new Macd(12, 26, 9).calculate(series(34)).points()).hasSize(1);
    }

    private static CandleSeries linear(int n) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BigDecimal p = BigDecimal.valueOf(i + 1L);
            Instant open = T0.plus(Timeframe.H1.duration().multipliedBy(i));
            candles.add(new Candle(BTC_USDT, Timeframe.H1, open, open.plus(Timeframe.H1.duration()).minusMillis(1),
                    p, p, p, p, BigDecimal.ONE, BigDecimal.ONE, 1));
        }
        return CandleSeries.of(BTC_USDT, Timeframe.H1, T0.plus(Timeframe.H1.duration().multipliedBy(n)), candles);
    }

    private static void assertClose(BigDecimal actual, String expected) {
        assertThat(actual).isCloseTo(new BigDecimal(expected), within(TOLERANCE));
    }
}
