package com.aurora.backend.batch.tasklet;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.aurora.backend.cart.repository.CartRepository;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CleanAbandonedCartsTasklet implements Tasklet {

    private final CartRepository cartRepository;
    private final long retentionHours;

    public CleanAbandonedCartsTasklet(
            CartRepository cartRepository,
            @Value("${app.batch.clean-abandoned-carts.retention-hours}") long retentionHours
    ) {
        this.cartRepository = cartRepository;
        this.retentionHours = retentionHours;
    }

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Instant cutoff = Instant.now().minus(retentionHours, ChronoUnit.HOURS);
        cartRepository.deleteByItemsIsEmptyAndUpdatedAtBefore(cutoff);
        return RepeatStatus.FINISHED;
    }
}
