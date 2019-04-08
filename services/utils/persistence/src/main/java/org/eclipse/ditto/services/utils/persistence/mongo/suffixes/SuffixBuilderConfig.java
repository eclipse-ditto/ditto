/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
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
