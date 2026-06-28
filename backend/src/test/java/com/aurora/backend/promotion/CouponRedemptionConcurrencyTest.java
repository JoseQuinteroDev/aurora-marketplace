package com.aurora.backend.promotion;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.aurora.backend.TestcontainersConfiguration;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.promotion.entity.Coupon;
import com.aurora.backend.promotion.entity.CouponType;
import com.aurora.backend.promotion.repository.CouponRepository;
import com.aurora.backend.promotion.repository.CouponUsageRepository;
import com.aurora.backend.promotion.service.CouponService;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;
import com.aurora.backend.user.role.Role;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves coupon-redemption is concurrency-safe (OWASP A04). A coupon with {@code maxUses=1}
 * redeemed by two transactions at once must be redeemed exactly ONCE — the previous
 * read-modify-write (count, then later save) let both pass. {@link CouponService#redeem} now
 * row-locks the coupon and re-checks the limit under the lock, so the second checkout blocks,
 * then sees the first redemption and is rejected. Requires Docker (Testcontainers PostgreSQL).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CouponRedemptionConcurrencyTest {

    @Autowired private CouponService couponService;
    @Autowired private CouponRepository couponRepository;
    @Autowired private CouponUsageRepository couponUsageRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void aCouponLimitedToOneUseCannotBeRedeemedTwiceConcurrently() throws Exception {
        Coupon coupon = couponRepository.save(new Coupon(
                "CONCUR-" + UUID.randomUUID().toString().substring(0, 8),
                CouponType.PERCENTAGE, new BigDecimal("10.00"), true,
                null, null, 1, null, null));
        User userA = newUser();
        User userB = newUser();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CyclicBarrier bothInTransaction = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<String> a = pool.submit(() -> redeem(tx, bothInTransaction, coupon, userA));
            Future<String> b = pool.submit(() -> redeem(tx, bothInTransaction, coupon, userB));

            String r1 = a.get(30, TimeUnit.SECONDS);
            String r2 = b.get(30, TimeUnit.SECONDS);

            assertThat(new String[]{r1, r2}).containsExactlyInAnyOrder("OK", "COUPON_MAX_USES_REACHED");
        } finally {
            pool.shutdownNow();
        }

        // Exactly one redemption was recorded — the global limit held under concurrency.
        assertThat(couponUsageRepository.countByCouponId(coupon.getId())).isEqualTo(1L);
    }

    private String redeem(TransactionTemplate tx, CyclicBarrier barrier, Coupon coupon, User user) {
        try {
            tx.executeWithoutResult(status -> {
                awaitQuietly(barrier);          // both transactions open before either takes the lock
                couponService.redeem(coupon, user);
            });
            return "OK";
        } catch (BusinessException rejected) {
            return rejected.getCode();
        }
    }

    private static void awaitQuietly(CyclicBarrier barrier) {
        try {
            barrier.await(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private User newUser() {
        return userRepository.save(
                new User("coupon-" + UUID.randomUUID() + "@aurora.test", "hash", "Cup", "On", Role.CUSTOMER, true));
    }
}
