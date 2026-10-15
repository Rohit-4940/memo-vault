package com.nexmemo.web.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded executor for compression work (Enterprise Phase 3).
 *
 * <p>The compressor must never run on the HTTP request thread and must never be able to spawn an
 * unbounded number of threads. This pool is therefore small, bounded and uses an
 * {@link ThreadPoolExecutor.AbortPolicy}: when the queue is full the submission is rejected and the
 * caller (the controller) marks the job failed, rather than silently blocking a request thread.</p>
 *
 * @author : NexMemo
 */
@Slf4j
@Configuration
public class AsyncConfig {

    public static final String COMPRESSION_EXECUTOR = "compressionExecutor";

    private final CompressionProperties properties;

    public AsyncConfig(CompressionProperties properties) {
        this.properties = properties;
    }

    @Bean(name = COMPRESSION_EXECUTOR)
    public TaskExecutor compressionExecutor() {
        CompressionProperties.Executor cfg = properties.getExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, cfg.getCorePoolSize()));
        executor.setMaxPoolSize(Math.max(cfg.getCorePoolSize(), cfg.getMaxPoolSize()));
        executor.setQueueCapacity(Math.max(1, cfg.getQueueCapacity()));
        executor.setKeepAliveSeconds(Math.max(0, cfg.getKeepAliveSeconds()));
        executor.setThreadNamePrefix("compress-");
        // Bounded + fail fast: never run compression on the caller thread, never queue forever.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        log.info("Compression executor ready (core={}, max={}, queue={})",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), cfg.getQueueCapacity());
        return executor;
    }
}
