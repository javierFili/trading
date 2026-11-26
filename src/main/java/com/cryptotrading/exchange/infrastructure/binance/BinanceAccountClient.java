package com.cryptotrading.exchange.infrastructure.binance;

import com.cryptotrading.exchange.domain.AccountClient;
import com.cryptotrading.exchange.domain.Balance;
import com.cryptotrading.exchange.domain.ClientOrderId;
import com.cryptotrading.exchange.domain.ExchangeException;
import com.cryptotrading.exchange.domain.ExchangeOrder;
import com.cryptotrading.exchange.domain.Fill;
import com.cryptotrading.shared.domain.Symbol;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Signed, read-only account endpoints (USER_DATA). */
final class BinanceAccountClient implements AccountClient {

    /** "Order does not exist." */
    static final String ERR_NO_SUCH_ORDER = "-2013";
    private static final TypeReference<List<BinanceDtos.OrderDto>> ORDERS = new TypeReference<>() {
    };
    private static final TypeReference<List<BinanceDtos.TradeDto>> TRADES = new TypeReference<>() {
    };

    private final BinanceHttp http;

    BinanceAccountClient(BinanceHttp http) {
        this.http = http;
    }

    @Override
    public List<Balance> balances() {
        BinanceDtos.Account account = http.read("/api/v3/account", Map.of("omitZeroBalances", "true"), 20, true,
                BinanceDtos.Account.class);
        return account.balances().stream().map(b -> new Balance(b.asset(), b.free(), b.locked())).toList();
    }

    @Override
    public Optional<ExchangeOrder> findOrder(Symbol symbol, ClientOrderId clientOrderId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", symbol.code());
        params.put("origClientOrderId", clientOrderId.value());
        try {
            return Optional.of(BinanceMapper.order(symbol,
                    http.read("/api/v3/order", params, 4, true, BinanceDtos.OrderDto.class)));
        } catch (ExchangeException.Rejected e) {
            if (ERR_NO_SUCH_ORDER.equals(e.errorCode())) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public List<ExchangeOrder> openOrders(Symbol symbol) {
        return http.read("/api/v3/openOrders", Map.of("symbol", symbol.code()), 6, true, ORDERS).stream()
                .map(o -> BinanceMapper.order(symbol, o)).toList();
    }

    @Override
    public List<Fill> fills(Symbol symbol, String exchangeOrderId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", symbol.code());
        params.put("orderId", exchangeOrderId);
        return http.read("/api/v3/myTrades", params, 5, true, TRADES).stream().map(BinanceMapper::fill).toList();
    }

    /** LIVE readiness: the API key must not be allowed to withdraw. Only exists on production. */
    boolean withdrawalsEnabled() {
        return http.read("/sapi/v1/account/apiRestrictions", Map.of(), 1, true, BinanceDtos.ApiRestrictions.class)
                .enableWithdrawals();
    }
}
