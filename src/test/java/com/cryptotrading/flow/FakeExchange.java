package com.cryptotrading.flow;

import com.cryptotrading.exchange.domain.AccountClient;
import com.cryptotrading.exchange.domain.Balance;
import com.cryptotrading.exchange.domain.ClientOrderId;
import com.cryptotrading.exchange.domain.ExchangeClient;
import com.cryptotrading.exchange.domain.ExchangeOrder;
import com.cryptotrading.exchange.domain.ExchangeOrderStatus;
import com.cryptotrading.exchange.domain.Fill;
import com.cryptotrading.exchange.domain.MarketDataClient;
import com.cryptotrading.exchange.domain.MarketDataStream;
import com.cryptotrading.exchange.domain.OcoRequest;
import com.cryptotrading.exchange.domain.OrderClient;
import com.cryptotrading.exchange.domain.OrderCommandResult;
import com.cryptotrading.exchange.domain.OrderListCommandResult;
import com.cryptotrading.exchange.domain.OrderRequest;
import com.cryptotrading.exchange.domain.OrderSide;
import com.cryptotrading.exchange.domain.OrderType;
import com.cryptotrading.exchange.domain.StreamSubscription;
import com.cryptotrading.exchange.domain.SymbolRules;
import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * In-memory spot exchange for end-to-end tests. MARKET orders fill immediately at {@link #price};
 * commissions are 0.1 % (base asset on buys, quote asset on sells). Thread-safe.
 */
public class FakeExchange implements ExchangeClient, MarketDataClient, AccountClient, OrderClient, MarketDataStream {

    public enum Behavior { NORMAL, UNKNOWN_BUT_EXECUTED, REJECT }

    static final BigDecimal FEE = new BigDecimal("0.001");

    private final Map<String, ExchangeOrder> orders = new HashMap<>();
    private final Map<String, List<Fill>> fills = new HashMap<>();
    private final Map<String, BigDecimal[]> balances = new HashMap<>();
    private final List<OrderRequest> submitted = new ArrayList<>();
    private BigDecimal price = new BigDecimal("1000");
    private Behavior behavior = Behavior.NORMAL;
    private long ids = 1;

    public FakeExchange() {
        reset();
    }

    public synchronized void reset() {
        orders.clear();
        fills.clear();
        submitted.clear();
        balances.clear();
        balances.put("USDT", new BigDecimal[]{new BigDecimal("100"), BigDecimal.ZERO});
        balances.put("BTC", new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        behavior = Behavior.NORMAL;
    }

    public synchronized void setPrice(BigDecimal price) {
        this.price = price;
    }

    public synchronized void setBehavior(Behavior behavior) {
        this.behavior = behavior;
    }

    public synchronized int submittedCount() {
        return submitted.size();
    }

    // ---------------- ExchangeClient ----------------
    @Override public String id() { return "FAKE_SPOT"; }
    @Override public MarketDataClient marketData() { return this; }
    @Override public MarketDataStream marketDataStream() { return this; }
    @Override public AccountClient account() { return this; }
    @Override public OrderClient orders() { return this; }

    // ---------------- Market data ----------------
    @Override public Instant serverTime() { return Instant.now(); }
    @Override public synchronized BigDecimal latestPrice(Symbol symbol) { return price; }
    @Override public List<Candle> candles(Symbol s, Timeframe t, Instant a, Instant b, int l) { return List.of(); }

    @Override
    public SymbolRules symbolRules(Symbol symbol) {
        return new SymbolRules(symbol, true, new BigDecimal("0.01"), new BigDecimal("0.01"), new BigDecimal("1000000"),
                new BigDecimal("0.00001"), new BigDecimal("0.00001"), new BigDecimal("9000"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("5"), null, Instant.now());
    }

    @Override
    public StreamSubscription subscribeClosedCandles(Symbol symbol, Timeframe timeframe, Consumer<Candle> listener) {
        return new StreamSubscription() {
            @Override public boolean isConnected() { return true; }
            @Override public void close() { }
        };
    }

    // ---------------- Account ----------------
    @Override
    public synchronized List<Balance> balances() {
        return balances.entrySet().stream().map(e -> new Balance(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
    }

    @Override
    public synchronized Optional<ExchangeOrder> findOrder(Symbol symbol, ClientOrderId id) {
        return Optional.ofNullable(orders.get(id.value()));
    }

    @Override
    public synchronized List<ExchangeOrder> openOrders(Symbol symbol) {
        return orders.values().stream().filter(o -> !o.status().isFinal()).toList();
    }

    @Override
    public synchronized List<Fill> fills(Symbol symbol, String exchangeOrderId) {
        return fills.getOrDefault(exchangeOrderId, List.of());
    }

    // ---------------- Orders ----------------
    @Override
    public synchronized OrderCommandResult submit(OrderRequest r) {
        submitted.add(r);
        if (behavior == Behavior.REJECT) {
            return new OrderCommandResult.Rejected(r.clientOrderId(), "-2010", "Account has insufficient balance");
        }
        if (orders.containsKey(r.clientOrderId().value())) {
            return new OrderCommandResult.Rejected(r.clientOrderId(), "-2010", "Duplicate order sent.");
        }
        if (r.type() != OrderType.MARKET) {
            throw new UnsupportedOperationException("Fake only supports MARKET orders");
        }
        String exchangeId = String.valueOf(ids++);
        BigDecimal quote = r.quantity().multiply(price);
        Fill fill;
        if (r.side() == OrderSide.BUY) {
            BigDecimal commission = r.quantity().multiply(FEE);
            add("USDT", quote.negate(), BigDecimal.ZERO);
            add("BTC", r.quantity().subtract(commission), BigDecimal.ZERO);
            fill = new Fill("t" + exchangeId, price, r.quantity(), quote, commission, "BTC", Instant.now());
        } else {
            BigDecimal commission = quote.multiply(FEE);
            add("BTC", r.quantity().negate(), BigDecimal.ZERO);
            add("USDT", quote.subtract(commission), BigDecimal.ZERO);
            fill = new Fill("t" + exchangeId, price, r.quantity(), quote, commission, "USDT", Instant.now());
        }
        ExchangeOrder order = new ExchangeOrder(exchangeId, r.clientOrderId(), r.symbol(), r.side(), r.type(),
                ExchangeOrderStatus.FILLED, r.quantity(), r.quantity(), quote, BigDecimal.ZERO, Instant.now());
        orders.put(r.clientOrderId().value(), order);
        fills.put(exchangeId, List.of(fill));
        return behavior == Behavior.UNKNOWN_BUT_EXECUTED
                ? new OrderCommandResult.Unknown(r.clientOrderId(), "TRANSPORT: read timed out")
                : new OrderCommandResult.Accepted(r.clientOrderId(), order);
    }

    @Override
    public synchronized OrderCommandResult cancel(Symbol symbol, ClientOrderId id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized OrderListCommandResult submitOco(OcoRequest r) {
        if (balances.get("BTC")[0].compareTo(r.quantity()) < 0) {
            return new OrderListCommandResult.Rejected(r.listClientOrderId(), "-2010", "insufficient balance");
        }
        add("BTC", r.quantity().negate(), r.quantity());
        ExchangeOrder tp = new ExchangeOrder(String.valueOf(ids++), r.takeProfitClientOrderId(), r.symbol(), OrderSide.SELL,
                OrderType.LIMIT_MAKER, ExchangeOrderStatus.NEW, r.quantity(), BigDecimal.ZERO, BigDecimal.ZERO,
                r.takeProfitPrice(), Instant.now());
        ExchangeOrder sl = new ExchangeOrder(String.valueOf(ids++), r.stopLossClientOrderId(), r.symbol(), OrderSide.SELL,
                OrderType.STOP_LOSS_LIMIT, ExchangeOrderStatus.NEW, r.quantity(), BigDecimal.ZERO, BigDecimal.ZERO,
                r.stopLimitPrice(), Instant.now());
        orders.put(tp.clientOrderId().value(), tp);
        orders.put(sl.clientOrderId().value(), sl);
        return new OrderListCommandResult.Accepted(r.listClientOrderId(), List.of(sl, tp));
    }

    @Override
    public synchronized OrderListCommandResult cancelOrderList(Symbol symbol, ClientOrderId listId) {
        List<ExchangeOrder> cancelled = new ArrayList<>();
        for (ExchangeOrder o : List.copyOf(orders.values())) {
            if (!o.status().isFinal() && o.type() != OrderType.MARKET) {
                ExchangeOrder c = with(o, ExchangeOrderStatus.CANCELED, BigDecimal.ZERO, BigDecimal.ZERO);
                orders.put(o.clientOrderId().value(), c);
                cancelled.add(c);
            }
        }
        BigDecimal locked = balances.get("BTC")[1];
        add("BTC", locked, locked.negate());
        return new OrderListCommandResult.Accepted(listId, cancelled);
    }

    /** Simulates the market reaching the take profit: TP leg fills, the stop leg expires. */
    public synchronized void fillTakeProfit() {
        for (ExchangeOrder o : List.copyOf(orders.values())) {
            if (o.status().isFinal()) {
                continue;
            }
            if (o.type() == OrderType.LIMIT_MAKER) {
                BigDecimal quote = o.originalQuantity().multiply(o.price());
                BigDecimal commission = quote.multiply(FEE);
                orders.put(o.clientOrderId().value(), with(o, ExchangeOrderStatus.FILLED, o.originalQuantity(), quote));
                fills.put(o.exchangeOrderId(), List.of(new Fill("t" + o.exchangeOrderId(), o.price(), o.originalQuantity(),
                        quote, commission, "USDT", Instant.now())));
                add("BTC", BigDecimal.ZERO, o.originalQuantity().negate());
                add("USDT", quote.subtract(commission), BigDecimal.ZERO);
            } else if (o.type() == OrderType.STOP_LOSS_LIMIT) {
                orders.put(o.clientOrderId().value(), with(o, ExchangeOrderStatus.EXPIRED, BigDecimal.ZERO, BigDecimal.ZERO));
            }
        }
    }

    private static ExchangeOrder with(ExchangeOrder o, ExchangeOrderStatus status, BigDecimal executed, BigDecimal quote) {
        return new ExchangeOrder(o.exchangeOrderId(), o.clientOrderId(), o.symbol(), o.side(), o.type(), status,
                o.originalQuantity(), executed, quote, o.price(), Instant.now());
    }

    private void add(String asset, BigDecimal free, BigDecimal locked) {
        BigDecimal[] b = balances.get(asset);
        b[0] = b[0].add(free);
        b[1] = b[1].add(locked);
    }
}
