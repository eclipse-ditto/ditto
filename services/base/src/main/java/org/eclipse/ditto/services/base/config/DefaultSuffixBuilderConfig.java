/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.base.config;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

/**
 * Holds the configuration for namespace appending to mongodb collection names.
 */
@Immutable
public final class DefaultSuffixBuilderConfig implements SuffixBuilderConfig {

    /**
     * An enumeration of known value paths and associated default values of the SuffixBuilderConfig.
     */
    enum SuffixBuilderConfigValue implements KnownConfigValue {

        EXTRACTOR_CLASS("class", ""),

        SUPPORTED_PREFIXES("supported-prefixes", Collections.emptyList());

        private final String path;
        private final Object defaultValue;

        private SuffixBuilderConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

    }

    /**
     * The supposed path of the MongoDB suffix-builder configuration object.
     */
    static final String CONFIG_PATH = "akka.contrib.persistence.mongodb.mongo.suffix-builder";

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSuffixBuilderConfig.class);

    private final List<String> supportedPrefixes;

    private DefaultSuffixBuilderConfig(final List<String> supportedPrefixes) {
        this.supportedPrefixes = Collections.unmodifiableList(new ArrayList<>(supportedPrefixes));
    }

    /**
     * Returns an instance of {@code DefaultSuffixBuilderConfig}.
     *
     * @param supportedPrefixes the already known supported prefixes.
     * @return the instance.
     * @throws NullPointerException if {@code supportedPrefixes} is {@code null}.
     */
    public static DefaultSuffixBuilderConfig of(final List<String> supportedPrefixes) {
        checkNotNull(supportedPrefixes, "supported prefixes");
        if (supportedPrefixes.isEmpty()) {
            LOGGER.warn("No prefixes are supported and therefore no namespace will never be appended. " +
                    "Please check your configuration.");
        }
        return new DefaultSuffixBuilderConfig(supportedPrefixes);
    }

    /**
     * Returns an instance of {@code DefaultSuffixBuilderConfig}.
     *
     * @param config the config which is supposed to contain path {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.base.config.DittoConfigError if
     * <ul>
     *     <li>{@code config} is {@code null},</li>
     *     <li>the value of {@code config} at {@value CONFIG_PATH} is not of type
     *     {@link com.typesafe.config.ConfigValueType#OBJECT} or</li>
     *     <li>the class set at {@code "class"} does not exist on the classpath.</li>
     * </ul>
     */
    public static DefaultSuffixBuilderConfig of(final Config config) {
        final Config configWithFallback =
                ConfigWithFallback.newInstance(config, CONFIG_PATH, SuffixBuilderConfigValue.values());

        final String extractorClassName =
                configWithFallback.getString(SuffixBuilderConfigValue.EXTRACTOR_CLASS.getPath());
        if (!extractorClassName.isEmpty()) {
            verifyIfExtractorClassIsAvailableInClasspath(extractorClassName);
        }

        return new DefaultSuffixBuilderConfig(
                configWithFallback.getStringList(SuffixBuilderConfigValue.SUPPORTED_PREFIXES.getPath()));
    }

    private static void verifyIfExtractorClassIsAvailableInClasspath(final String extractorClassName) {
        try {
            Class.forName(extractorClassName);
        } catch (final ClassNotFoundException | NoClassDefFoundError e) {
            final String msgPattern = "The configured class to extract namespace suffixes <{0}> is not available at " +
                    "the classpath! Please check the config path <{1}>.";
            final String qualifiedConfigPath = CONFIG_PATH + "." + SuffixBuilderConfigValue.EXTRACTOR_CLASS.getPath();
            throw new DittoConfigError(MessageFormat.format(msgPattern, extractorClassName, qualifiedConfigPath), e);
        }
    }

    /**
     * @return a list of which entity suffixes (e.g. "thing", "policy") are supported,
     */
    @Override
    public List<String> getSupportedPrefixes() {
        return supportedPrefixes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultSuffixBuilderConfig that = (DefaultSuffixBuilderConfig) o;
        return supportedPrefixes.equals(that.supportedPrefixes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(supportedPrefixes);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "supportedPrefixes=" + supportedPrefixes +
                "]";
    }

}
