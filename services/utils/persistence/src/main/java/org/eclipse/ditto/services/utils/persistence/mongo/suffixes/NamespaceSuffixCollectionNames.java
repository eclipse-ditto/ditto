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

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.contrib.persistence.mongodb.CanSuffixCollectionNames;

/**
 * Class to get suffix of a collection name based on the persistenceId of an entity.
 */
public final class NamespaceSuffixCollectionNames implements CanSuffixCollectionNames {

    private static final Logger LOGGER = LoggerFactory.getLogger(NamespaceSuffixCollectionNames.class);

    private static final char[] FORBIDDEN_CHARS_IN_MONGO_COLLECTION_NAMES = new char[]{'$'};

    private static final String REPLACE_REGEX =
            String.format("[%s]", Pattern.quote(new String(FORBIDDEN_CHARS_IN_MONGO_COLLECTION_NAMES)));

    static final int MAX_SUFFIX_CHARS_LENGTH = 45;

    private static SuffixBuilderConfig suffixBuilderConfig;

    /**
     * Injects the {@link SuffixBuilderConfig} to use for the instance of this service.
     *
     * @param suffixBuilderConfig the SuffixBuilderConfig to use
     */
    public static void setConfig(final SuffixBuilderConfig suffixBuilderConfig) {
        NamespaceSuffixCollectionNames.suffixBuilderConfig = suffixBuilderConfig;
        LOGGER.info("Namespace appending to mongodb collection names is enabled");
    }

    /**
     * Resets the SuffixBuilderConfig to use.
     */
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

        final String[] persistenceIdSplitByColons = persistenceId.split(":");

        if (persistenceIdSplitByColons.length < 2) {
            throw new PersistenceIdInvalidException(persistenceId);
        }

        final String prefix = persistenceIdSplitByColons[0];
        if (!suffixBuilderConfig.getSupportedPrefixes().contains(prefix)) {
            return "";
        }

        return validateMongoCharacters(persistenceIdSplitByColons[1]); // take the namespace of the entity
    }

    /**
     * Removes all characters that are forbidden in mongodb collection names.
     *
     * @param input The original input
     * @return The input without forbidden characters which  are replaced by "#"
     */
    @Override
    public String validateMongoCharacters(final String input) {
        return doValidateMongoCharacters(input);
    }

    /**
     * Removes all characters that are forbidden in mongodb collection names.
     *
     * @param input The original input
     * @return The input without forbidden characters which  are replaced by "#"
     */
    static String doValidateMongoCharacters(final String input) {
        final String escaped = input.replaceAll(REPLACE_REGEX, "#");
        // the max length of a collection or index name (including the DB name) in MongoDB is 120 bytes
        // so we assume that we need ~66 characters for the longest "static" part of the collection/index name, e.g.:
        // "concierge.batch_supervisor_journal@...$batch_supervisor_journal_index" and leave some spare
        // characters for the added hash
        if (escaped.length() > MAX_SUFFIX_CHARS_LENGTH) {
            final String hash = Integer.toHexString(escaped.hashCode());
            return escaped.substring(0, MAX_SUFFIX_CHARS_LENGTH) + "@" + hash;
        }
        return escaped;
    }

    public static final class PersistenceIdInvalidException extends RuntimeException {

        private static final long serialVersionUID = -4789912839628096316L;

        private  PersistenceIdInvalidException(final String persistenceId){
            super(String.format("Persistence id <%s> is not in the expected format of <prefix:namespace:name>", persistenceId));
        }
    }
}
