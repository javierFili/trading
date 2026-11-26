package com.cryptotrading.market.domain;

import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses kline CSV files published at data.binance.vision:
 * {@code open_time,open,high,low,close,volume,close_time,quote_volume,count,taker_buy_base,taker_buy_quote,ignore}.
 * Spot files from 2025 onwards use microsecond timestamps; both units are accepted. Header lines are skipped.
 */
public final class BinanceCsvParser {

    private static final long MICROS_THRESHOLD = 100_000_000_000_000L;

    private BinanceCsvParser() {
    }

    public static List<Candle> parse(Symbol symbol, Timeframe timeframe, Reader reader) {
        List<Candle> candles = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(reader)) {
            String line;
            int lineNumber = 0;
            while ((line = in.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || !Character.isDigit(line.charAt(0))) {
                    continue;
                }
                String[] f = line.split(",");
                if (f.length < 9) {
                    throw new IllegalArgumentException("Line " + lineNumber + ": expected >= 9 columns");
                }
                candles.add(new Candle(symbol, timeframe, instant(f[0]), instant(f[6]),
                        new BigDecimal(f[1].trim()), new BigDecimal(f[2].trim()), new BigDecimal(f[3].trim()),
                        new BigDecimal(f[4].trim()), new BigDecimal(f[5].trim()), new BigDecimal(f[7].trim()),
                        Long.parseLong(f[8].trim())));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return candles;
    }

    static Instant instant(String raw) {
        long value = Long.parseLong(raw.trim());
        return value >= MICROS_THRESHOLD
                ? Instant.ofEpochSecond(value / 1_000_000, (value % 1_000_000) * 1_000)
                : Instant.ofEpochMilli(value);
    }
}
