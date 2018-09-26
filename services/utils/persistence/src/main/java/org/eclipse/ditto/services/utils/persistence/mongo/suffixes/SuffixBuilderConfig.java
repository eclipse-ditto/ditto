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

    private final List<String> supportedPrefixes;


    public SuffixBuilderConfig(final List<String> supportedPrefixes) {
        this.supportedPrefixes = supportedPrefixes;

        if (supportedPrefixes.isEmpty()) {
            LOGGER.warn("No prefixes are supported and therefore no namespace will never be appended. " +
                    "Please check your configuration.");
        }
    }

    /**
     * @return a list of which entity suffixes (e.g. "thing", "policy") are supported,
     */
    List<String> getSupportedPrefixes() {
        return new ArrayList<>(supportedPrefixes);
    }
}
