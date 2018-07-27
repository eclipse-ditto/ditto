package org.eclipse.ditto.services.utils.persistence.mongo.suffixes;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.services.base.config.AbstractConfigReader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.ConfigurationException;

public final class SuffixBuilderConfigReader extends AbstractConfigReader {

    private static final String PATH = "akka.contrib.persistence.mongodb.mongo.suffix-builder";

    private final SuffixBuilderConfig suffixBuilderConfig;

    /**
     * Creates a AbstractConfigReader.
     *
     * @param config the underlying Config object.
     */
    private SuffixBuilderConfigReader(final Config config) {
        super(config);

        final String enabledPropertyName = "enabled";
        final boolean defaultValue = true;
        final boolean enabled = getIfPresent(enabledPropertyName, config::getBoolean).orElse(defaultValue);

        final String supportedPrefixesPropertyName = "supported-prefixes";
        final List<String> supportedPrefixes = getIfPresent(supportedPrefixesPropertyName, config::getStringList)
                .orElse(Collections.emptyList());

        if (enabled) {
            verifyClassIsDefinedAndAvailableInClasspath();
        }

        this.suffixBuilderConfig = new SuffixBuilderConfig(enabled, supportedPrefixes);
    }

    /**
     * Create a headers configuration reader from an unrelativized configuration object.
     *
     * @param rawConfig the raw configuration.
     * @return a headers configuration reader.
     */
    public static SuffixBuilderConfigReader fromRawConfig(final Config rawConfig) {
        final Config suffixBuilderConfig = rawConfig.hasPath(PATH)
                ? rawConfig.getConfig(PATH)
                : ConfigFactory.empty();
        return new SuffixBuilderConfigReader(suffixBuilderConfig);
    }

    public SuffixBuilderConfig getSuffixBuilderConfig() {
        return suffixBuilderConfig;
    }

    private void verifyClassIsDefinedAndAvailableInClasspath() {
        final String classPropertyName = "class";
        final String fullQualifiedPropertyName = PATH + "." + classPropertyName;
        final String extractorClass = getIfPresent(classPropertyName, config::getString).orElseThrow(
                () -> new ConfigurationException(
                        String.format("SuffixBuilder is enabled but no class is defined. Please define property <%s>.",
                                fullQualifiedPropertyName)));

        try {
            Class.forName(extractorClass);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            final String message = String.format(
                    "The configured class to extract namespace suffixes <%s> is not available in the classpath." +
                            " Please check the property <%s>.",
                    extractorClass,
                    fullQualifiedPropertyName);

            throw new ConfigurationException(message);
        }
    }
}
