package com.gullakx.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@ComponentScan(basePackages = {"com.gullakx"})
@EnableCaching
public class WalletServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletServiceApplication.class, args);
    }
}
