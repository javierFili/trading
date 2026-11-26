package com.cryptotrading.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Bean is only created when the platform talks to an exchange (TESTNET or LIVE), never in BACKTEST. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnExpression("'${trading.mode:BACKTEST}' != 'BACKTEST'")
public @interface ConditionalOnExchangeMode {
}
