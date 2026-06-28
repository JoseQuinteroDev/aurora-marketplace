package com.aurora.backend.order;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.aurora.backend.TestcontainersConfiguration;
import com.aurora.backend.order.entity.Order;
import com.aurora.backend.order.entity.OrderStatus;
import com.aurora.backend.order.repository.OrderRepository;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;
import com.aurora.backend.user.role.Role;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the {@code @Version} optimistic lock on {@link Order} (OWASP A04 — concurrency
 * integrity) actually prevents a lost update: two transactions that both read the same order
 * and then write it must NOT both succeed. A {@link CyclicBarrier} forces both reads to happen
 * before either commit, so the second flush reliably hits the stale-version conflict.
 *
 * <p>Requires Docker (Testcontainers PostgreSQL via {@link TestcontainersConfiguration}); the
 * raw {@link ObjectOptimisticLockingFailureException} is what {@code GlobalExceptionHandler}
 * maps to a 409.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrderOptimisticLockingTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void twoConcurrentWritesToTheSameOrderDoNotBothSucceed() throws Exception {
        UUID orderId = seedOrder();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CyclicBarrier bothHaveRead = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<String> a = pool.submit(() -> writeStatus(tx, bothHaveRead, orderId, OrderStatus.PAID));
            Future<String> b = pool.submit(() -> writeStatus(tx, bothHaveRead, orderId, OrderStatus.CANCELLED));

            String r1 = a.get(20, TimeUnit.SECONDS);
            String r2 = b.get(20, TimeUnit.SECONDS);

            // Exactly one writer wins; the other loses the optimistic-lock race.
            assertThat(new String[]{r1, r2}).containsExactlyInAnyOrder("OK", "CONFLICT");
        } finally {
            pool.shutdownNow();
        }

        // The winning write is durable and the version advanced past its initial 0.
        Order persisted = orderRepository.findById(orderId).orElseThrow();
        assertThat(persisted.getStatus()).isIn(OrderStatus.PAID, OrderStatus.CANCELLED);
        assertThat(persisted.getVersion()).isGreaterThanOrEqualTo(1L);
    }

    private String writeStatus(TransactionTemplate tx, CyclicBarrier barrier, UUID orderId, OrderStatus target) {
        try {
            tx.executeWithoutResult(status -> {
                Order order = orderRepository.findById(orderId).orElseThrow();
                awaitQuietly(barrier);                 // ensure both transactions read version 0 first
                order.changeStatus(target);
                orderRepository.saveAndFlush(order);   // flush forces the versioned UPDATE now
            });
            return "OK";
        } catch (ObjectOptimisticLockingFailureException conflict) {
            return "CONFLICT";
        }
    }

    private static void awaitQuietly(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private UUID seedOrder() {
        User user = userRepository.save(
                new User("lock-" + UUID.randomUUID() + "@aurora.test", "hash", "Lock", "Test", Role.CUSTOMER, true));
        Order order = new Order(
                "ORD-" + UUID.randomUUID(), user, OrderStatus.PENDING_PAYMENT, null,
                new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("10.00"));
        return orderRepository.save(order).getId();
    }
}
