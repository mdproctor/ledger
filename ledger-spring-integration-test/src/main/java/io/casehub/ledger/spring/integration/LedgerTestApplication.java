package io.casehub.ledger.spring.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;

import io.casehub.platform.testing.spring.SpringTestConfig;

@SpringBootApplication
@EntityScan("io.casehub.ledger")
@Import(SpringTestConfig.class)
public class LedgerTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerTestApplication.class, args);
    }
}
