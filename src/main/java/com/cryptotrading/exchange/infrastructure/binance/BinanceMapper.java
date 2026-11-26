package com.cryptotrading.exchange.infrastructure.binance;

import com.cryptotrading.exchange.domain.ClientOrderId;
import com.cryptotrading.exchange.domain.ExchangeOrder;
import com.cryptotrading.exchange.domain.ExchangeOrderStatus;
import com.cryptotrading.exchange.domain.Fill;
import com.cryptotrading.exchange.domain.OrderSide;
import com.cryptotrading.exchange.domain.OrderType;
import com.cryptotrading.exchange.domain.SymbolRules;
import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Translation between Binance wire formats and exchange-neutral domain types. */
final class BinanceMapper {

    private BinanceMapper() {
    }

    static String interval(Timeframe timeframe) {
        return switch (timeframe) {
            case M1 -> "1m";
            case M5 -> "5m";
            case M15 -> "15m";
            case H1 -> "1h";
            case H4 -> "4h";
            case D1 -> "1d";
        };
    }

    static ExchangeOrderStatus status(String binanceStatus) {
        return switch (binanceStatus) {
            case "NEW", "PENDING_NEW" -> ExchangeOrderStatus.NEW;
            case "PARTIALLY_FILLED" -> ExchangeOrderStatus.PARTIALLY_FILLED;
            case "FILLED" -> ExchangeOrderStatus.FILLED;
            case "CANCELED", "PENDING_CANCEL" -> ExchangeOrderStatus.CANCELED;
            case "REJECTED" -> ExchangeOrderStatus.REJECTED;
            case "EXPIRED", "EXPIRED_IN_MATCH" -> ExchangeOrderStatus.EXPIRED;
            default -> throw new IllegalArgumentException("Unknown Binance order status: " + binanceStatus);
        };
    }

    static ExchangeOrder order(Symbol symbol, BinanceDtos.OrderDto dto) {
        String clientId = dto.origClientOrderId() != null ? dto.origClientOrderId() : dto.clientOrderId();
        Long time = dto.updateTime() != null ? dto.updateTime() : dto.transactTime();
        return new ExchangeOrder(String.valueOf(dto.orderId()), new ClientOrderId(clientId), symbol,
                OrderSide.valueOf(dto.side()), OrderType.valueOf(dto.type()), status(dto.status()),
                dto.origQty(), dto.executedQty(), dto.cummulativeQuoteQty(), dto.price(),
                time == null ? null : Instant.ofEpochMilli(time));
    }

    static List<Fill> fills(BinanceDtos.OrderDto dto, Instant at) {
        if (dto.fills() == null) {
            return List.of();
        }
        List<Fill> result = new ArrayList<>();
        for (BinanceDtos.FillDto f : dto.fills()) {
            result.add(new Fill(String.valueOf(f.tradeId()), f.price(), f.qty(), f.price().multiply(f.qty()),
                    f.commission(), f.commissionAsset(), at));
        }
        return result;
    }

    static Fill fill(BinanceDtos.TradeDto t) {
        return new Fill(String.valueOf(t.id()), t.price(), t.qty(), t.quoteQty(), t.commission(),
                t.commissionAsset(), Instant.ofEpochMilli(t.time()));
    }

    /** Parses one row of {@code GET /api/v3/klines}: [openTime, o, h, l, c, v, closeTime, quoteVolume, trades, ...]. */
    static Candle kline(Symbol symbol, Timeframe timeframe, List<Object> row) {
        return new Candle(symbol, timeframe,
                Instant.ofEpochMilli(((Number) row.get(0)).longValue()),
                Instant.ofEpochMilli(((Number) row.get(6)).longValue()),
                decimal(row.get(1)), decimal(row.get(2)), decimal(row.get(3)), decimal(row.get(4)),
                decimal(row.get(5)), decimal(row.get(7)), ((Number) row.get(8)).longValue());
    }

    static Candle kline(Symbol symbol, Timeframe timeframe, BinanceDtos.Kline k) {
        return new Candle(symbol, timeframe, Instant.ofEpochMilli(k.openTime()), Instant.ofEpochMilli(k.closeTime()),
                k.open(), k.high(), k.low(), k.close(), k.volume(), k.quoteVolume(), k.tradeCount());
    }

    static SymbolRules rules(Symbol symbol, BinanceDtos.SymbolInfo info, Instant fetchedAt) {
        Map<String, Object> price = filter(info, "PRICE_FILTER");
        Map<String, Object> lot = filter(info, "LOT_SIZE");
        Map<String, Object> marketLot = filter(info, "MARKET_LOT_SIZE");
        Map<String, Object> notional = filter(info, "NOTIONAL");
        if (notional.isEmpty()) {
            notional = filter(info, "MIN_NOTIONAL");
        }
        return new SymbolRules(symbol, "TRADING".equals(info.status()),
                decimalOrNull(price.get("tickSize")), decimalOrNull(price.get("minPrice")), decimalOrNull(price.get("maxPrice")),
                decimalOrNull(lot.get("stepSize")), decimalOrNull(lot.get("minQty")), decimalOrNull(lot.get("maxQty")),
                decimalOrNull(marketLot.get("stepSize")), decimalOrNull(marketLot.get("minQty")), decimalOrNull(marketLot.get("maxQty")),
                decimalOrNull(notional.get("minNotional")), decimalOrNull(notional.get("maxNotional")),
                fetchedAt);
    }

    private static Map<String, Object> filter(BinanceDtos.SymbolInfo info, String type) {
        if (info.filters() == null) {
            return Map.of();
        }
        return info.filters().stream().filter(f -> type.equals(f.get("filterType"))).findFirst().orElse(Map.of());
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    private static BigDecimal decimalOrNull(Object value) {
        return value == null ? null : decimal(value);
    }
}
