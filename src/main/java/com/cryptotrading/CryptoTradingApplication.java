package com.cryptotrading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CryptoTradingApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoTradingApplication.class, args);
    }
}
