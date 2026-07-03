package com.banking.mts;

import com.banking.mts.dto.*;
import com.banking.mts.service.AccountService;
import com.banking.mts.service.TransferService;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Sql(scripts = "/init.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ConcurrencyTest {

    @Container
    static final MSSQLServerContainer<?> sqlServer = new MSSQLServerContainer<>(
            DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
            .acceptLicense()
            .withPassword("P@ssw0rd1234")
            .withInitScript("init.sql");

    @Container
    static final RedisContainer redis = new RedisContainer(
            DockerImageName.parse("redis:7-alpine"));

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlserver://" + sqlServer.getHost() + ":" + sqlServer.getMappedPort(1433)
                        + ";databaseName=mts_db;encrypt=true;trustServerCertificate=true");
        registry.add("spring.datasource.username", sqlServer::getUsername);
        registry.add("spring.datasource.password", sqlServer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    private Long fromAccountId;
    private Long toAccountId;

    @BeforeEach
    void setUp() {
        AccountResponse from = accountService.createAccount(
                CreateAccountRequest.builder()
                        .ownerName("Alice")
                        .initialBalance("1000")
                        .currency("THB")
                        .build());
        AccountResponse to = accountService.createAccount(
                CreateAccountRequest.builder()
                        .ownerName("Bob")
                        .initialBalance("0")
                        .currency("THB")
                        .build());
        this.fromAccountId = from.getId();
        this.toAccountId = to.getId();
    }

    @Test
    void concurrentWithdraw_shouldNotGoNegative() throws InterruptedException {
        int threads = 10;
        BigDecimal amountPerThread = new BigDecimal("100");
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    accountService.withdraw(fromAccountId,
                            WithdrawRequest.builder().amount(amountPerThread).build());
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        BalanceResponse balance = accountService.getBalanceById(fromAccountId);
        BigDecimal expectedTotalWithdrawn = amountPerThread.multiply(new BigDecimal(successes.get()));
        BigDecimal expectedRemaining = new BigDecimal("1000").subtract(expectedTotalWithdrawn);

        assertEquals(0, balance.getBalance().compareTo(expectedRemaining),
                "Final balance should be exactly " + expectedRemaining);
        assertTrue(balance.getBalance().compareTo(BigDecimal.ZERO) >= 0,
                "Balance must not be negative: " + balance.getBalance());
        assertTrue(failures.get() > 0 || successes.get() == 10,
                "If all succeed, balance should be 0; otherwise some should fail due to insufficient balance");
    }

    @Test
    void concurrentTransfer_shouldNotDoubleSpend() throws InterruptedException {
        int threads = 10;
        BigDecimal amountPerThread = new BigDecimal("100");
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            String idempotencyKey = "transfer-concurrent-" + UUID.randomUUID();
            executor.submit(() -> {
                try {
                    transferService.createTransfer(
                            CreateTransferRequest.builder()
                                    .fromAccountId(fromAccountId)
                                    .toAccountId(toAccountId)
                                    .amount(amountPerThread.toString())
                                    .currency("THB")
                                    .build(),
                            idempotencyKey);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        BalanceResponse fromBalance = accountService.getBalanceById(fromAccountId);
        BalanceResponse toBalance = accountService.getBalanceById(toAccountId);

        BigDecimal expectedTotalTransferred = amountPerThread.multiply(new BigDecimal(successes.get()));
        BigDecimal expectedFromRemaining = new BigDecimal("1000").subtract(expectedTotalTransferred);

        assertEquals(0, fromBalance.getBalance().compareTo(expectedFromRemaining),
                "Source balance should be " + expectedFromRemaining);
        assertEquals(0, toBalance.getBalance().compareTo(expectedTotalTransferred),
                "Destination balance should be " + expectedTotalTransferred);
        assertTrue(fromBalance.getBalance().compareTo(BigDecimal.ZERO) >= 0,
                "Source balance must not be negative");
    }
}
