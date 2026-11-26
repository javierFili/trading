package com.cryptotrading.exchange.infrastructure.binance;

import com.cryptotrading.exchange.domain.ClientOrderId;
import com.cryptotrading.exchange.domain.ExchangeException;
import com.cryptotrading.exchange.domain.OcoRequest;
import com.cryptotrading.exchange.domain.OrderClient;
import com.cryptotrading.exchange.domain.OrderCommandResult;
import com.cryptotrading.exchange.domain.OrderListCommandResult;
import com.cryptotrading.exchange.domain.OrderRequest;
import com.cryptotrading.shared.domain.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Signed trading endpoints. Never retries. Outcome classification (see ADR-0006):
 * <ul>
 *   <li>2xx → Accepted</li>
 *   <li>HTTP 5xx, error -1007 (backend timeout) or a transport failure → Unknown (must reconcile)</li>
 *   <li>other 4xx / 429 / 418 / local rate budget → Rejected (nothing happened on the exchange)</li>
 * </ul>
 */
final class BinanceOrderClient implements OrderClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceOrderClient.class);
    static final int ERR_BACKEND_TIMEOUT = -1007;

    private final BinanceHttp http;
    private final tools.jackson.databind.json.JsonMapper json;

    BinanceOrderClient(BinanceHttp http, tools.jackson.databind.json.JsonMapper json) {
        this.http = http;
        this.json = json;
    }

    @Override
    public OrderCommandResult submit(OrderRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", request.symbol().code());
        params.put("side", request.side().name());
        params.put("type", request.type().name());
        params.put("quantity", request.quantity().toPlainString());
        if (request.price() != null) {
            params.put("price", request.price().toPlainString());
        }
        if (request.stopPrice() != null) {
            params.put("stopPrice", request.stopPrice().toPlainString());
        }
        if (request.timeInForce() != null) {
            params.put("timeInForce", request.timeInForce().name());
        }
        params.put("newClientOrderId", request.clientOrderId().value());
        params.put("newOrderRespType", "FULL");
        return single(request.clientOrderId(), request.symbol(), HttpMethod.POST, "/api/v3/order", params);
    }

    @Override
    public OrderCommandResult cancel(Symbol symbol, ClientOrderId clientOrderId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", symbol.code());
        params.put("origClientOrderId", clientOrderId.value());
        return single(clientOrderId, symbol, HttpMethod.DELETE, "/api/v3/order", params);
    }

    @Override
    public OrderListCommandResult submitOco(OcoRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", request.symbol().code());
        params.put("listClientOrderId", request.listClientOrderId().value());
        params.put("side", "SELL");
        params.put("quantity", request.quantity().toPlainString());
        params.put("aboveType", "LIMIT_MAKER");
        params.put("abovePrice", request.takeProfitPrice().toPlainString());
        params.put("aboveClientOrderId", request.takeProfitClientOrderId().value());
        params.put("belowType", "STOP_LOSS_LIMIT");
        params.put("belowStopPrice", request.stopTriggerPrice().toPlainString());
        params.put("belowPrice", request.stopLimitPrice().toPlainString());
        params.put("belowTimeInForce", "GTC");
        params.put("belowClientOrderId", request.stopLossClientOrderId().value());
        params.put("newOrderRespType", "FULL");
        return list(request.listClientOrderId(), request.symbol(), HttpMethod.POST, "/api/v3/orderList/oco", params);
    }

    @Override
    public OrderListCommandResult cancelOrderList(Symbol symbol, ClientOrderId listClientOrderId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", symbol.code());
        params.put("listClientOrderId", listClientOrderId.value());
        return list(listClientOrderId, symbol, HttpMethod.DELETE, "/api/v3/orderList", params);
    }

    private OrderCommandResult single(ClientOrderId id, Symbol symbol, HttpMethod method, String path,
                                      Map<String, String> params) {
        return classify(method, path, params,
                body -> new OrderCommandResult.Accepted(id,
                        BinanceMapper.order(symbol, json.readValue(body, BinanceDtos.OrderDto.class))),
                (code, msg) -> new OrderCommandResult.Rejected(id, code, msg),
                reason -> new OrderCommandResult.Unknown(id, reason));
    }

    private OrderListCommandResult list(ClientOrderId id, Symbol symbol, HttpMethod method, String path,
                                        Map<String, String> params) {
        return classify(method, path, params,
                body -> {
                    BinanceDtos.OrderListDto dto = json.readValue(body, BinanceDtos.OrderListDto.class);
                    return new OrderListCommandResult.Accepted(id,
                            dto.orderReports().stream().map(o -> BinanceMapper.order(symbol, o)).toList());
                },
                (code, msg) -> new OrderListCommandResult.Rejected(id, code, msg),
                reason -> new OrderListCommandResult.Unknown(id, reason));
    }

    private <R> R classify(HttpMethod method, String path, Map<String, String> params, Function<String, R> accepted,
                           java.util.function.BiFunction<String, String, R> rejected, Function<String, R> unknown) {
        BinanceHttp.RawResponse response;
        try {
            response = http.send(method, path, params, 1);
        } catch (ExchangeException.RateLimited e) {
            return rejected.apply("LOCAL_RATE_LIMIT", e.getMessage());
        } catch (BinanceHttp.TransportException e) {
            log.warn("{} {} outcome unknown: {}", method, path, e.getMessage());
            return unknown.apply("TRANSPORT: " + e.getMessage());
        }
        if (response.isSuccess()) {
            try {
                return accepted.apply(response.body());
            } catch (RuntimeException e) {
                // The exchange accepted the request but we cannot read the response: state must be reconciled.
                return unknown.apply("UNPARSEABLE_SUCCESS_RESPONSE");
            }
        }
        BinanceHttp.BinanceError error = http.parseError(response.body());
        if (response.status() >= 500 || error.code() == ERR_BACKEND_TIMEOUT) {
            return unknown.apply("HTTP " + response.status() + " code " + error.code() + ": " + error.msg());
        }
        if (response.status() == 429 || response.status() == 418) {
            return rejected.apply("RATE_LIMITED", "HTTP " + response.status());
        }
        return rejected.apply(String.valueOf(error.code()), error.msg());
    }
}
