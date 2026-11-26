package com.cryptotrading.market.domain;

import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Timeframe;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.Instant;
import java.util.List;

import static com.cryptotrading.shared.domain.CandleFixtures.BTC_USDT;
import static org.assertj.core.api.Assertions.assertThat;

class BinanceCsvParserTest {

    @Test
    void parsesMillisecondAndMicrosecondFilesAndSkipsHeaders() {
        String csv = """
                open_time,open,high,low,close,volume,close_time,quote_volume,count,taker_buy_volume,taker_buy_quote_volume,ignore
                1704067200000,42283.58,42554.57,42261.02,42475.23,1271.68108,1704070799999,53957248.97,47134,682.57,28957416.82,0
                1735689600000000,93576.00,94509.42,93489.03,94401.14,755.00,1735693199999999,70841109.55,123,1,1,0
                """;
        List<Candle> candles = BinanceCsvParser.parse(BTC_USDT, Timeframe.H1, new StringReader(csv));
        assertThat(candles).hasSize(2);
        assertThat(candles.get(0).openTime()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
        assertThat(candles.get(0).close()).isEqualByComparingTo("42475.23");
        assertThat(candles.get(1).openTime()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(candles.get(1).closeTime()).isEqualTo(Instant.parse("2025-01-01T00:59:59.999999Z"));
    }
}
