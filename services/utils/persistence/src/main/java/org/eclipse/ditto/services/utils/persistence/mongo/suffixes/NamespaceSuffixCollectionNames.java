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

import akka.contrib.persistence.mongodb.CanSuffixCollectionNames;

/**
 * Class to get suffix of a collection name based on the persistenceId of an entity.
 */
public class NamespaceSuffixCollectionNames implements CanSuffixCollectionNames {

    private static final char[] forbiddenCharactersInMongoCollectionNames =
            new char[]{'/', '\\', '.', ' ', '\"', '$', '*', '<', '>', ':', '|', '?'};

    private static final String REPLACE_REGEX =
            String.format("[%s]", Pattern.quote(new String(forbiddenCharactersInMongoCollectionNames)));

    /**
     * Gets the suffix of the collection name for the given persistenceId.
     *
     * @param persistenceId The persistenceId of the entity.
     * @return The suffix for the collection name without any forbidden characters.
     */
    @Override
    public String getSuffixFromPersistenceId(final String persistenceId) {
        if (!shouldSuffix(persistenceId)) {
            return "";
        }

        final String[] persistenceIdSplitByColons = persistenceId.split(":");

        if (persistenceIdSplitByColons.length < 3) {
            throw new IllegalStateException("Missing namespace in persistenceId: " + persistenceId);
        }

        return validateMongoCharacters(persistenceIdSplitByColons[1]);
    }

    private static boolean shouldSuffix(final String persistenceId) {
        return persistenceId.startsWith("thing:") || persistenceId.startsWith("policy:") ||
                persistenceId.startsWith("connection:");
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

        return input.replace('.', '%').replaceAll(REPLACE_REGEX, "#");
    }
}
