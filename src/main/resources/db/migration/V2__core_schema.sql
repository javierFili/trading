-- V2: core data model (see docs/DATA_MODEL.md). Money/prices: NUMERIC(38,18). Times: TIMESTAMPTZ (UTC).

CREATE TABLE app_user (
    id               UUID PRIMARY KEY,
    username         VARCHAR(50)  NOT NULL UNIQUE,
    password_hash    VARCHAR(255) NOT NULL,
    role             VARCHAR(20)  NOT NULL CHECK (role IN ('VIEWER', 'TRADER')),
    totp_secret_enc  BYTEA,
    totp_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    totp_last_step   BIGINT,
    failed_attempts  INT          NOT NULL DEFAULT 0,
    locked_until     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0
);

-- Credentials are NOT stored here: they come from environment variables (docs/SECURITY.md).
CREATE TABLE trading_account (
    id          UUID PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    exchange    VARCHAR(30) NOT NULL,
    mode        VARCHAR(10) NOT NULL CHECK (mode IN ('TESTNET', 'LIVE')),
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (exchange, mode, name)
);
INSERT INTO trading_account (id, name, exchange, mode) VALUES
    ('00000000-0000-0000-0000-000000000001', 'default', 'BINANCE_SPOT', 'TESTNET'),
    ('00000000-0000-0000-0000-000000000002', 'default', 'BINANCE_SPOT', 'LIVE');

CREATE TABLE trading_pair (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    base_asset           VARCHAR(20)    NOT NULL,
    quote_asset          VARCHAR(20)    NOT NULL,
    symbol_code          VARCHAR(40)    NOT NULL UNIQUE,
    enabled              BOOLEAN        NOT NULL DEFAULT TRUE,
    trading_enabled      BOOLEAN        NOT NULL DEFAULT TRUE,
    tick_size            NUMERIC(38,18),
    min_price            NUMERIC(38,18),
    max_price            NUMERIC(38,18),
    step_size            NUMERIC(38,18),
    min_qty              NUMERIC(38,18),
    max_qty              NUMERIC(38,18),
    market_step_size     NUMERIC(38,18),
    market_min_qty       NUMERIC(38,18),
    market_max_qty       NUMERIC(38,18),
    min_notional         NUMERIC(38,18),
    max_notional         NUMERIC(38,18),
    filters_fetched_at   TIMESTAMPTZ,
    UNIQUE (base_asset, quote_asset)
);
-- Seed filters are a starting point for backtests only; they are refreshed from exchangeInfo when online.
INSERT INTO trading_pair (base_asset, quote_asset, symbol_code, tick_size, min_price, max_price, step_size,
                          min_qty, max_qty, market_step_size, market_min_qty, market_max_qty, min_notional,
                          max_notional, filters_fetched_at)
VALUES ('BTC', 'USDT', 'BTCUSDT', 0.01, 0.01, 1000000, 0.00001, 0.00001, 9000, 0.00001, 0.00001, 100,
        5, 9000000, '2026-11-18T00:00:00Z');

CREATE TABLE market_candle (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pair_id       BIGINT         NOT NULL REFERENCES trading_pair (id),
    timeframe     VARCHAR(5)     NOT NULL,
    open_time     TIMESTAMPTZ    NOT NULL,
    close_time    TIMESTAMPTZ    NOT NULL,
    open          NUMERIC(38,18) NOT NULL CHECK (open > 0),
    high          NUMERIC(38,18) NOT NULL,
    low           NUMERIC(38,18) NOT NULL CHECK (low > 0),
    close         NUMERIC(38,18) NOT NULL CHECK (close > 0),
    volume        NUMERIC(38,18) NOT NULL CHECK (volume >= 0),
    quote_volume  NUMERIC(38,18) NOT NULL CHECK (quote_volume >= 0),
    trade_count   BIGINT         NOT NULL CHECK (trade_count >= 0),
    source        VARCHAR(20)    NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (high >= low AND high >= open AND high >= close AND low <= open AND low <= close),
    CHECK (close_time > open_time),
    UNIQUE (pair_id, timeframe, open_time)
);

CREATE TABLE trading_strategy (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL,
    version      VARCHAR(50)  NOT NULL,
    timeframe    VARCHAR(5)   NOT NULL,
    parameters   JSONB        NOT NULL,
    description  TEXT,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (code, version)
);

CREATE TABLE market_indicator (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pair_id         BIGINT         NOT NULL REFERENCES trading_pair (id),
    timeframe       VARCHAR(5)     NOT NULL,
    candle_open_time TIMESTAMPTZ   NOT NULL,
    strategy_id     BIGINT         NOT NULL REFERENCES trading_strategy (id),
    indicator_name  VARCHAR(50)    NOT NULL,
    value           NUMERIC(38,18) NOT NULL,
    UNIQUE (pair_id, timeframe, candle_open_time, strategy_id, indicator_name)
);

CREATE TABLE strategy_signal (
    id                UUID PRIMARY KEY,
    strategy_id       BIGINT         NOT NULL REFERENCES trading_strategy (id),
    pair_id           BIGINT         NOT NULL REFERENCES trading_pair (id),
    timeframe         VARCHAR(5)     NOT NULL,
    candle_open_time  TIMESTAMPTZ    NOT NULL,
    signal_type       VARCHAR(20)    NOT NULL CHECK (signal_type IN ('ENTRY_LONG', 'EXIT_LONG')),
    reference_price   NUMERIC(38,18) NOT NULL,
    score             NUMERIC(5,2)   NOT NULL CHECK (score BETWEEN 0 AND 100),
    indicators        JSONB          NOT NULL,
    reasons           JSONB          NOT NULL,
    invalidation      JSONB          NOT NULL,
    suggested_stop    NUMERIC(38,18),
    suggested_tp      NUMERIC(38,18),
    trading_mode      VARCHAR(10)    NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL,
    UNIQUE (strategy_id, pair_id, timeframe, candle_open_time)
);
CREATE INDEX ix_signal_created ON strategy_signal (created_at DESC);

CREATE TABLE trading_position (
    id                  UUID PRIMARY KEY,
    account_id          UUID           NOT NULL REFERENCES trading_account (id),
    pair_id             BIGINT         NOT NULL REFERENCES trading_pair (id),
    status              VARCHAR(10)    NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    entry_qty           NUMERIC(38,18) NOT NULL CHECK (entry_qty > 0),
    avg_entry_price     NUMERIC(38,18) NOT NULL,
    entry_quote         NUMERIC(38,18) NOT NULL,
    exit_qty            NUMERIC(38,18),
    avg_exit_price      NUMERIC(38,18),
    exit_quote          NUMERIC(38,18),
    stop_loss           NUMERIC(38,18) NOT NULL,
    take_profit         NUMERIC(38,18) NOT NULL,
    fees_quote          NUMERIC(38,18) NOT NULL DEFAULT 0,
    realized_pnl        NUMERIC(38,18),
    exit_reason         VARCHAR(20),
    protected           BOOLEAN        NOT NULL DEFAULT FALSE,
    opened_at           TIMESTAMPTZ    NOT NULL,
    closed_at           TIMESTAMPTZ,
    version             BIGINT         NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_one_open_position ON trading_position (account_id, pair_id) WHERE status = 'OPEN';
CREATE INDEX ix_position_closed_at ON trading_position (account_id, closed_at);

CREATE TABLE trade_proposal (
    id               UUID PRIMARY KEY,
    signal_id        UUID           REFERENCES strategy_signal (id),
    account_id       UUID           NOT NULL REFERENCES trading_account (id),
    pair_id          BIGINT         NOT NULL REFERENCES trading_pair (id),
    position_id      UUID           REFERENCES trading_position (id),
    purpose          VARCHAR(10)    NOT NULL CHECK (purpose IN ('ENTRY', 'EXIT')),
    side             VARCHAR(4)     NOT NULL CHECK (side IN ('BUY', 'SELL')),
    reference_price  NUMERIC(38,18) NOT NULL,
    stop_loss        NUMERIC(38,18),
    take_profit      NUMERIC(38,18),
    quantity         NUMERIC(38,18) NOT NULL CHECK (quantity > 0),
    notional         NUMERIC(38,18) NOT NULL,
    risk_amount      NUMERIC(38,18),
    est_fees         NUMERIC(38,18) NOT NULL,
    reward_risk      NUMERIC(38,18),
    justification    JSONB          NOT NULL,
    status           VARCHAR(20)    NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED',
                         'EXPIRED', 'EXECUTING', 'PARTIALLY_FILLED', 'FILLED', 'FAILED', 'CANCELLED')),
    status_reason    VARCHAR(500),
    content_hash     VARCHAR(64)       NOT NULL,
    expires_at       TIMESTAMPTZ    NOT NULL,
    trading_mode     VARCHAR(10)    NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_proposal_status ON trade_proposal (status, expires_at);
CREATE INDEX ix_proposal_created ON trade_proposal (created_at DESC);

CREATE TABLE trade_approval (
    id                    UUID PRIMARY KEY,
    proposal_id           UUID         NOT NULL UNIQUE REFERENCES trade_proposal (id),
    proposal_version      BIGINT       NOT NULL,
    proposal_content_hash VARCHAR(64)     NOT NULL,
    decision              VARCHAR(10)  NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    decided_by            VARCHAR(50)  NOT NULL,
    decided_at            TIMESTAMPTZ  NOT NULL,
    client_ip             VARCHAR(64)
);

CREATE TABLE trading_order (
    id                    UUID PRIMARY KEY,
    proposal_id           UUID           REFERENCES trade_proposal (id),
    position_id           UUID           REFERENCES trading_position (id),
    account_id            UUID           NOT NULL REFERENCES trading_account (id),
    pair_id               BIGINT         NOT NULL REFERENCES trading_pair (id),
    role                  VARCHAR(20)    NOT NULL CHECK (role IN ('ENTRY', 'EXIT', 'TAKE_PROFIT', 'STOP_LOSS')),
    client_order_id       VARCHAR(36)    NOT NULL,
    list_client_order_id  VARCHAR(36),
    exchange_order_id     VARCHAR(40),
    side                  VARCHAR(4)     NOT NULL,
    type                  VARCHAR(20)    NOT NULL,
    quantity              NUMERIC(38,18) NOT NULL,
    price                 NUMERIC(38,18),
    stop_price            NUMERIC(38,18),
    status                VARCHAR(20)    NOT NULL CHECK (status IN ('PENDING_SUBMIT', 'UNKNOWN', 'NEW',
                              'PARTIALLY_FILLED', 'FILLED', 'CANCELED', 'REJECTED', 'EXPIRED')),
    executed_qty          NUMERIC(38,18) NOT NULL DEFAULT 0,
    cumulative_quote_qty  NUMERIC(38,18) NOT NULL DEFAULT 0,
    status_reason         VARCHAR(500),
    fills_recorded        BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL,
    last_reconciled_at    TIMESTAMPTZ,
    version               BIGINT         NOT NULL DEFAULT 0,
    UNIQUE (account_id, client_order_id)
);
CREATE INDEX ix_order_status ON trading_order (status);

CREATE TABLE trade_execution (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id          UUID           NOT NULL REFERENCES trading_order (id),
    exchange_trade_id VARCHAR(40)    NOT NULL,
    price             NUMERIC(38,18) NOT NULL,
    quantity          NUMERIC(38,18) NOT NULL,
    quote_quantity    NUMERIC(38,18) NOT NULL,
    commission        NUMERIC(38,18) NOT NULL,
    commission_asset  VARCHAR(20)    NOT NULL,
    executed_at       TIMESTAMPTZ    NOT NULL,
    UNIQUE (order_id, exchange_trade_id)
);

CREATE TABLE portfolio_snapshot (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id         UUID           NOT NULL REFERENCES trading_account (id),
    taken_at           TIMESTAMPTZ    NOT NULL,
    balances           JSONB          NOT NULL,
    total_value_quote  NUMERIC(38,18) NOT NULL,
    realized_pnl_day   NUMERIC(38,18) NOT NULL
);
CREATE INDEX ix_snapshot_account_time ON portfolio_snapshot (account_id, taken_at DESC);

CREATE TABLE risk_event (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id   UUID REFERENCES trading_account (id),
    proposal_id  UUID REFERENCES trade_proposal (id),
    rule         VARCHAR(50)  NOT NULL,
    severity     VARCHAR(10)  NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    detail       VARCHAR(1000) NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_risk_event_time ON risk_event (occurred_at DESC);

CREATE TABLE circuit_breaker (
    account_id  UUID PRIMARY KEY REFERENCES trading_account (id),
    is_open     BOOLEAN      NOT NULL DEFAULT FALSE,
    reason      VARCHAR(500),
    consecutive_failures INT NOT NULL DEFAULT 0,
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    changed_by  VARCHAR(50),
    version     BIGINT       NOT NULL DEFAULT 0
);
INSERT INTO circuit_breaker (account_id) VALUES
    ('00000000-0000-0000-0000-000000000001'), ('00000000-0000-0000-0000-000000000002');

CREATE TABLE backtest_run (
    id               UUID PRIMARY KEY,
    group_id         UUID           NOT NULL,
    strategy_code    VARCHAR(50)    NOT NULL,
    strategy_version VARCHAR(50)    NOT NULL,
    parameters       JSONB          NOT NULL,
    symbol_code      VARCHAR(40)    NOT NULL,
    timeframe        VARCHAR(5)     NOT NULL,
    period_start     TIMESTAMPTZ    NOT NULL,
    period_end       TIMESTAMPTZ    NOT NULL,
    dataset_split    VARCHAR(12)    NOT NULL CHECK (dataset_split IN ('FULL', 'TRAIN', 'VALIDATION', 'TEST')),
    fee_rate         NUMERIC(10,8)  NOT NULL,
    slippage_rate    NUMERIC(10,8)  NOT NULL,
    initial_capital  NUMERIC(38,18) NOT NULL,
    risk_limits      JSONB          NOT NULL,
    metrics          JSONB,
    data_checksum    VARCHAR(64),
    candle_count     INT,
    status           VARCHAR(10)    NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    error            VARCHAR(1000),
    created_by       VARCHAR(50)    NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL,
    finished_at      TIMESTAMPTZ
);
CREATE INDEX ix_backtest_group ON backtest_run (group_id);

CREATE TABLE backtest_trade (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id       UUID           NOT NULL REFERENCES backtest_run (id) ON DELETE CASCADE,
    entry_time   TIMESTAMPTZ    NOT NULL,
    entry_price  NUMERIC(38,18) NOT NULL,
    exit_time    TIMESTAMPTZ    NOT NULL,
    exit_price   NUMERIC(38,18) NOT NULL,
    quantity     NUMERIC(38,18) NOT NULL,
    fees         NUMERIC(38,18) NOT NULL,
    pnl          NUMERIC(38,18) NOT NULL,
    exit_reason  VARCHAR(20)    NOT NULL
);
CREATE INDEX ix_backtest_trade_run ON backtest_trade (run_id, entry_time);
