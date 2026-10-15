package com.nexmemo.web.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Policy for the document vault (Enterprise Phase 4): storage quota, external-sharing rules and the
 * inline-preview pipeline. All values are enforced <b>server-side</b>; the client can never widen
 * them.
 *
 * <p>Bound from {@code nexmemo.documents.*}. The legacy {@code nexmemo.documents.share-base-url}
 * key is read separately by {@code DocumentService} and is ignored here.</p>
 *
 * @author : NexMemo
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "nexmemo.documents")
public class DocumentProperties {

    private Quota quota = new Quota();
    private Share share = new Share();
    private Preview preview = new Preview();

    @Getter
    @Setter
    public static class Quota {
        /** When false, uploads are never rejected for size (usage is still reported). */
        private boolean enabled = false;
        /** Per-user live-bytes ceiling. Default 1 GiB. */
        private long bytesPerUser = 1024L * 1024 * 1024;
    }

    @Getter
    @Setter
    public static class Share {
        /** Allow external share links for CONFIDENTIAL documents. Off by default. */
        private boolean allowConfidential = false;
        /** Allow external share links for RESTRICTED documents. Off by default. */
        private boolean allowRestricted = false;
    }

    @Getter
    @Setter
    public static class Preview {
        /** Master switch for the inline-preview pipeline. */
        private boolean enabled = true;
        /** Largest file that may be treated as plain text and rendered inline. Default 2 MiB. */
        private long maxTextBytes = 2L * 1024 * 1024;
        /** How many leading bytes are inspected for magic-byte detection. */
        private int maxHeadBytes = 4096;
    }
}
