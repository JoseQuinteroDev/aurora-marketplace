package com.aurora.backend.batch.config;

import com.aurora.backend.batch.support.BatchJobAuditListener;
import com.aurora.backend.batch.tasklet.CleanAbandonedCartsTasklet;
import com.aurora.backend.batch.tasklet.ImportProductsTasklet;
import com.aurora.backend.batch.tasklet.SyncInventoryTasklet;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchJobConfig {

    @Bean
    public Job importProductsJob(
            JobRepository jobRepository,
            Step importProductsStep,
            BatchJobAuditListener listener
    ) {
        return new JobBuilder("importProductsJob", jobRepository)
                .listener(listener)
                .start(importProductsStep)
                .build();
    }

    @Bean
    public Step importProductsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ImportProductsTasklet importProductsTasklet
    ) {
        return new StepBuilder("importProductsStep", jobRepository)
                .tasklet(importProductsTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job syncInventoryJob(
            JobRepository jobRepository,
            Step syncInventoryStep,
            BatchJobAuditListener listener
    ) {
        return new JobBuilder("syncInventoryJob", jobRepository)
                .listener(listener)
                .start(syncInventoryStep)
                .build();
    }

    @Bean
    public Step syncInventoryStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SyncInventoryTasklet syncInventoryTasklet
    ) {
        return new StepBuilder("syncInventoryStep", jobRepository)
                .tasklet(syncInventoryTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job cleanAbandonedCartsJob(
            JobRepository jobRepository,
            Step cleanAbandonedCartsStep,
            BatchJobAuditListener listener
    ) {
        return new JobBuilder("cleanAbandonedCartsJob", jobRepository)
                .listener(listener)
                .start(cleanAbandonedCartsStep)
                .build();
    }

    @Bean
    public Step cleanAbandonedCartsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            CleanAbandonedCartsTasklet cleanAbandonedCartsTasklet
    ) {
        return new StepBuilder("cleanAbandonedCartsStep", jobRepository)
                .tasklet(cleanAbandonedCartsTasklet, transactionManager)
                .build();
    }
}
