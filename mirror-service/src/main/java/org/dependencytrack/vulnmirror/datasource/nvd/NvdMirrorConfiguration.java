package org.dependencytrack.vulnmirror.datasource.nvd;

import io.github.jeremylong.openvulnerability.client.nvd.NvdApiException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.dependencytrack.vulnmirror.datasource.util.LoggingRejectedExecutionHandler;
import org.dependencytrack.vulnmirror.datasource.util.LoggingUncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff;

class NvdMirrorConfiguration {

    @Produces
    @ForNvdMirror
    @ApplicationScoped
    ExecutorService executorService() {
        final Logger nvdMirrorLogger = LoggerFactory.getLogger(NvdMirror.class);

        final var threadFactory = new BasicThreadFactory.Builder()
                .namingPattern("hyades-mirror-nvd-%d")
                .uncaughtExceptionHandler(new LoggingUncaughtExceptionHandler(nvdMirrorLogger))
                .build();

        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1), threadFactory, new LoggingRejectedExecutionHandler(nvdMirrorLogger));
    }

    @Produces
    @ForNvdMirror
    @ApplicationScoped
    Timer durationTimer(final MeterRegistry meterRegistry) {
        return Timer.builder("mirror.nvd.duration")
                .description("Duration of NVD mirroring operations")
                .register(meterRegistry);
    }

    @Produces
    @ForNvdMirror
    @ApplicationScoped
    Retry createRetry(NvdConfig config) {
        final RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom().
                intervalFunction(ofExponentialBackoff(
                        Duration.ofSeconds(config.retryBackoffInitialDurationSeconds()),
                        config.retryBackoffMultiplier(), Duration.ofSeconds(config.retryMaxDuration())))
                .maxAttempts(config.retryMaxAttempts())
                .retryOnException(NvdApiException.class::isInstance)
                .retryOnResult(response -> false)
                .build());

        return retryRegistry.retry("nvdMirrorRetry");
    }
}
