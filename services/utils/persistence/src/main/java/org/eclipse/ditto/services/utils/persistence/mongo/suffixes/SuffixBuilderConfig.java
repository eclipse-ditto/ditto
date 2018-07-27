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
package org.eclipse.ditto.services.utils.persistence.mongo.suffixes;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the configuration for namespace appending to mongodb collection names.
 */
public final class SuffixBuilderConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(SuffixBuilderConfig.class);

    private final boolean enabled;
    private final List<String> supportedPrefixes;


    SuffixBuilderConfig(final boolean enabled, final List<String> supportedPrefixes) {
        this.enabled = enabled;
        this.supportedPrefixes = supportedPrefixes;

        if (enabled && supportedPrefixes.isEmpty()) {
            LOGGER.warn("Namespace appending for mongodb collection names is enabled, but no prefixes are supported." +
                    "Namespace will never be appended. Please check your configuration.");
        }
    }

    boolean isEnabled() {
        return enabled;
    }

    List<String> getSupportedPrefixes() {
        return new ArrayList<>(supportedPrefixes);
    }
}
