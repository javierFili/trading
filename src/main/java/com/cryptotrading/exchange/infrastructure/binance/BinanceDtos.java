package com.cryptotrading.exchange.infrastructure.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Wire formats of the Binance Spot REST/WebSocket API. Decimal fields are parsed from strings exactly. */
final class BinanceDtos {

    private BinanceDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TickerPrice(String symbol, BigDecimal price) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExchangeInfo(List<SymbolInfo> symbols) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SymbolInfo(String symbol, String status, String baseAsset, String quoteAsset,
                      List<Map<String, Object>> filters) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Account(List<BalanceDto> balances) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BalanceDto(String asset, BigDecimal free, BigDecimal locked) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderDto(String symbol, long orderId, Long orderListId, String clientOrderId, String origClientOrderId,
                    BigDecimal price, BigDecimal origQty, BigDecimal executedQty, BigDecimal cummulativeQuoteQty,
                    String status, String type, String side, BigDecimal stopPrice, Long updateTime,
                    Long transactTime, List<FillDto> fills) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FillDto(BigDecimal price, BigDecimal qty, BigDecimal commission, String commissionAsset, Long tradeId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderListDto(long orderListId, String listClientOrderId, String listOrderStatus,
                        List<OrderDto> orderReports) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TradeDto(long id, long orderId, BigDecimal price, BigDecimal qty, BigDecimal quoteQty,
                    BigDecimal commission, String commissionAsset, long time) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiRestrictions(boolean enableWithdrawals, boolean enableSpotAndMarginTrading, boolean ipRestrict) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KlineEvent(@JsonProperty("e") String eventType, @JsonProperty("k") Kline kline) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Kline(@JsonProperty("t") long openTime, @JsonProperty("T") long closeTime,
                 @JsonProperty("s") String symbol, @JsonProperty("i") String interval,
                 @JsonProperty("o") BigDecimal open, @JsonProperty("h") BigDecimal high,
                 @JsonProperty("l") BigDecimal low, @JsonProperty("c") BigDecimal close,
                 @JsonProperty("v") BigDecimal volume, @JsonProperty("q") BigDecimal quoteVolume,
                 @JsonProperty("n") long tradeCount, @JsonProperty("x") boolean closed) {
    }
}
