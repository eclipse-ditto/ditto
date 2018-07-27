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

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.contrib.persistence.mongodb.CanSuffixCollectionNames;

/**
 * Class to get suffix of a collection name based on the persistenceId of an entity.
 */
public class NamespaceSuffixCollectionNames implements CanSuffixCollectionNames {

    private static final Logger LOGGER = LoggerFactory.getLogger(NamespaceSuffixCollectionNames.class);

    private static final char[] forbiddenCharactersInMongoCollectionNames =
            new char[]{'/', '\\', '.', ' ', '\"', '$', '*', '<', '>', ':', '|', '?'};

    private static final String REPLACE_REGEX =
            String.format("[%s]", Pattern.quote(new String(forbiddenCharactersInMongoCollectionNames)));

    private static SuffixBuilderConfig suffixBuilderConfig;

    public static void setConfig(final SuffixBuilderConfig suffixBuilderConfig) {
        NamespaceSuffixCollectionNames.suffixBuilderConfig = suffixBuilderConfig;
        LOGGER.debug("Configured " + NamespaceSuffixCollectionNames.class.getName());
        if (suffixBuilderConfig.isEnabled()) {
            LOGGER.info("Namespace appending to mongodb collection names is enabled");
        } else {
            LOGGER.info("Namespace appending to mongodb collection names is disabled");
        }
    }

    static void resetConfig() {
        suffixBuilderConfig = null;
    }

    /**
     * Gets the suffix of the collection name for the given persistenceId.
     *
     * @param persistenceId The persistenceId of the entity.
     * @return The suffix for the collection name without any forbidden characters.
     */
    @Override
    public String getSuffixFromPersistenceId(final String persistenceId) {

        if (!suffixBuilderConfig.isEnabled()) {
            return "";
        }

        final String[] persistenceIdSplitByColons = persistenceId.split(":");

        if (persistenceIdSplitByColons.length < 3) {
            throw new IllegalStateException(
                    String.format("Persistence id <%s> is not in the expected format of <prefix:namespace:name>",
                            persistenceId));
        }

        final String prefix = persistenceIdSplitByColons[0];
        if (!suffixBuilderConfig.getSupportedPrefixes().contains(prefix)) {
            return "";
        }

        return validateMongoCharacters(persistenceIdSplitByColons[1]);
    }

    /**
     * Removes all characters that are forbidden in mongodb collection names.
     *
     * @param input The original input
     * @return The input without forbidden characters. Dots will be replaced by "%" all other forbidden
     * characters are replaced by "#"
     */
    @Override
    public String validateMongoCharacters(final String input) {

        if (!suffixBuilderConfig.isEnabled()) {
            return input;
        }
        return input.replace('.', '%').replaceAll(REPLACE_REGEX, "#");
    }
}
