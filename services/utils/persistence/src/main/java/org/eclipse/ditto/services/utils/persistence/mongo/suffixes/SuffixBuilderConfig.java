package org.eclipse.ditto.services.utils.persistence.mongo.suffixes;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SuffixBuilderConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(SuffixBuilderConfig.class);

    private final boolean enabled;
    private final List<String> supportedPrefixes;


    public SuffixBuilderConfig(final boolean enabled, final List<String> supportedPrefixes) {
        this.enabled = enabled;
        this.supportedPrefixes = supportedPrefixes;

        if (enabled && supportedPrefixes.isEmpty()) {
            LOGGER.warn("Namespace appending for mongodb collection names is enabled, but no prefixes are supported." +
                    "Namespace will never be appended. Please check your configuration.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getSupportedPrefixes() {
        return new ArrayList<>(supportedPrefixes);
    }
}
