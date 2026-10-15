package com.nexmemo.web.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Tunables for the asynchronous, secure compressor (Enterprise Phase 3).
 *
 * <p>Lives under {@code nexmemo.compression} alongside the legacy {@code enabled}/{@code type}
 * switches so the whole compressor is configured from one place.</p>
 */
@Getter @Setter
@Configuration
@ConfigurationProperties(prefix = "nexmemo.compression")
public class CompressionProperties {

    /** Hard ceiling on an accepted upload, before any work is scheduled. */
    private long maxInputBytes = 1024L * 1024 * 1024;

    /** Worker pool sizing. Deliberately small and bounded — compression is CPU/IO heavy. */
    private Executor executor = new Executor();

    /** Archive bomb / traversal guardrails. */
    private Guardrails guardrails = new Guardrails();

    @Getter @Setter
    public static class Executor {
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 200;
        /** Seconds an idle worker is kept alive above the core size. */
        private int keepAliveSeconds = 60;
    }

    @Getter @Setter
    public static class Guardrails {
        /** Whether archives found among inputs are structurally inspected (bomb/traversal). */
        private boolean inspectArchives = true;

        /** Whether inputs are handed to ClamAV before being processed. */
        private boolean scanWithClamAv = true;

        /** Maximum number of entries an input archive may contain. */
        private int maxEntries = 2000;

        /** Maximum total uncompressed size an input archive may expand to. */
        private long maxUncompressedBytes = 500L * 1024 * 1024;

        /** Maximum allowed uncompressed:compressed ratio (bomb heuristic). */
        private int maxCompressionRatio = 200;

        /** Maximum number of nested archive levels we will descend into. */
        private int maxNestingDepth = 1;
    }
}
