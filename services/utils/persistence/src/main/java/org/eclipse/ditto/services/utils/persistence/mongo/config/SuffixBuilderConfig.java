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
package org.eclipse.ditto.services.utils.persistence.mongo.config;

import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings of the MongoDB suffix builder for the Akka persistence plugin.
 * <p>
 * Java serialization is supported for {@code SuffixBuilderConfig}.
 * </p>
 */
@Immutable
public interface SuffixBuilderConfig {

    /**
     * Provides the supported prefixes of MongoDB collection name suffixes.
     *
     * @return the supported prefixes.
     */
    List<String> getSupportedPrefixes();

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
        public String getConfigPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

    }

}
