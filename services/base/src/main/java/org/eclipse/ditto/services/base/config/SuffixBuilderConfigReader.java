/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.base.config;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.persistence.mongo.suffixes.SuffixBuilderConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.ConfigurationException;

/**
 * Reads and validates the configuration for namespace appending to mongodb collection names.
 */
public final class SuffixBuilderConfigReader extends AbstractConfigReader {

    private static final String PATH = "akka.contrib.persistence.mongodb.mongo.suffix-builder";

    @Nullable private final SuffixBuilderConfig suffixBuilderConfig;

    /**
     * Creates a AbstractConfigReader.
     *
     * @param config the underlying Config object.
     */
    private SuffixBuilderConfigReader(final Config config) {
        super(config);

        final String supportedPrefixesPropertyName = "supported-prefixes";
        final List<String> supportedPrefixes = getIfPresent(supportedPrefixesPropertyName, config::getStringList)
                .orElse(Collections.emptyList());

        final String extractorClass = getIfPresent("class", config::getString).orElse("");
        final boolean enabled = !extractorClass.equals("");

        if (enabled) {
            verifyClassIsAvailableInClasspath(extractorClass);
            this.suffixBuilderConfig = new SuffixBuilderConfig(supportedPrefixes);
        } else {
            suffixBuilderConfig = null;
        }
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

    /**
     * @return the {@link SuffixBuilderConfig} which is used in this reader.
     */
    public Optional<SuffixBuilderConfig> getSuffixBuilderConfig() {
        if (suffixBuilderConfig == null) {
            return Optional.empty();
        } else {
            return Optional.of(suffixBuilderConfig);
        }
    }

    private void verifyClassIsAvailableInClasspath(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            final String fullQualifiedPropertyName = PATH + "." + "class";
            final String message = String.format(
                    "The configured class to extract namespace suffixes <%s> is not available in the classpath." +
                            " Please check the property <%s>.",
                    className,
                    fullQualifiedPropertyName);

            throw new ConfigurationException(message);
        }
    }
}
