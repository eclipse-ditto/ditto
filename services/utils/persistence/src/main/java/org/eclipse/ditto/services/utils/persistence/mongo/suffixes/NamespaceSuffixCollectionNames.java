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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.contrib.persistence.mongodb.CanSuffixCollectionNames;

/**
 * Class to get suffix of a collection name based on the persistence ID of an entity.
 */
public final class NamespaceSuffixCollectionNames implements CanSuffixCollectionNames {

    static final int MAX_SUFFIX_CHARS_LENGTH = 45;

    private static final Logger LOGGER = LoggerFactory.getLogger(NamespaceSuffixCollectionNames.class);

    private static final char[] FORBIDDEN_CHARS_IN_MONGO_COLLECTION_NAMES = new char[]{'$'};

    private static final String REPLACE_REGEX =
            String.format("[%s]", Pattern.quote(new String(FORBIDDEN_CHARS_IN_MONGO_COLLECTION_NAMES)));

    private static final Collection<String> SUPPORTED_PREFIXES = new HashSet<>();

    /**
     * Globally sets the provided supported prefixes.
     * The currently set prefixes are completely replaced.
     *
     * @param supportedPrefixes the prefixes that are supported by this service.
     * @throws NullPointerException if {@code supportedPrefixes} is {@code null}.
     */
    public static void setSupportedPrefixes(final Collection<String> supportedPrefixes) {
        checkNotNull(supportedPrefixes, "supported prefixes");
        resetConfig();
        SUPPORTED_PREFIXES.addAll(supportedPrefixes);
        LOGGER.info("Namespace appending to MongoDB collection names is enabled.");
    }

    /**
     * Resets the SuffixBuilderConfig to use.
     */
    static void resetConfig() {
        SUPPORTED_PREFIXES.clear();
    }

    /**
     * Gets the suffix of the collection name for the given persistence ID.
     *
     * @param persistenceId the persistence ID of the entity.
     * @return the suffix for the collection name without any forbidden characters.
     * @throws PersistenceIdInvalidException if {@code persistenceId} did not contain at least two colons ({@code ":"}.
     */
    @Override
    public String getSuffixFromPersistenceId(final String persistenceId) {
        final String[] persistenceIdSplitByColons = persistenceId.split(":");
        if (persistenceIdSplitByColons.length < 2) {
            throw new PersistenceIdInvalidException(persistenceId);
        }

        final String prefix = persistenceIdSplitByColons[0];
        if (!SUPPORTED_PREFIXES.contains(prefix)) {
            return "";
        }

        return validateMongoCharacters(persistenceIdSplitByColons[1]); // take the namespace of the entity
    }

    /**
     * Removes all characters that are forbidden in mongodb collection names.
     *
     * @param input the original input.
     * @return the input without forbidden characters which  are replaced by "#".
     */
    @Override
    public String validateMongoCharacters(final String input) {
        return doValidateMongoCharacters(input);
    }

    /**
     * Removes all characters that are forbidden in MongoDB collection names.
     *
     * @param input the original input.
     * @return the input without forbidden characters which  are replaced by "#".
     */
    static String doValidateMongoCharacters(final String input) {
        final String escaped = input.replaceAll(REPLACE_REGEX, "#");
        // the max length of a collection or index name (including the DB name) in MongoDB is 120 bytes
        // so we assume that we need ~66 characters for the longest "static" part of the collection/index name, e.g.:
        // "database.things_journal@...$things_journal_index" and leave some spare
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
            super(MessageFormat.format("Persistence ID <{0}> is not in the expected format of <prefix:namespace:name>!",
                    persistenceId));
        }

    }

}
