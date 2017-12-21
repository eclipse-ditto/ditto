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
package org.eclipse.ditto.signals.commands.thingsearch.query;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidFilterException;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidOptionException;

/**
 * Defines constants for testing.
 */
public final class TestConstants {

    /**
     * A known correlation id for testing.
     */
    public static final String CORRELATION_ID = "a780b7b5-fdd2-4864-91fc-80df6bb0a636";

    /**
     * Known command headers.
     */
    public static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .correlationId(CORRELATION_ID)
            .authorizationSubjects("the_subject", "another_subject").build();

    /**
     * Empty command headers.
     */
    public static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    /**
     * A known timestamp.
     */
    public static final Instant TIMESTAMP = Instant.EPOCH;
    /**
     * Known JSON parse options.
     */
    public static final JsonParseOptions JSON_PARSE_OPTIONS =
            JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();
    /**
     * A known JSON field selector.
     */
    public static final JsonFieldSelector JSON_FIELD_SELECTOR_ATTRIBUTES =
            JsonFactory.newFieldSelector("attributes(location,maker)", JSON_PARSE_OPTIONS);
    /**
     * A known JSON field selector.
     */
    public static final JsonFieldSelector JSON_FIELD_SELECTOR_ATTRIBUTES_WITH_THING_ID =
            JsonFactory.newFieldSelector("thingId,attributes(location,maker)", JSON_PARSE_OPTIONS);
    /**
     * A known JSON field selector.
     */
    public static final JsonFieldSelector JSON_FIELD_SELECTOR_FEATURE_PROPERTIES =
            JsonFactory.newFieldSelector("properties/target_year_1", JSON_PARSE_OPTIONS);

    private TestConstants() {
        throw new AssertionError();
    }


    /**
     * Search-related test constants.
     */
    public static final class Search {

        /**
         * A known filter string.
         */
        public static final String KNOWN_FILTER_STR = "eq(thingId,4711)";

        /**
         * A known option.
         */
        public static final String KNOWN_OPT_1 = "opt1";
        /**
         * Another known option.
         */
        public static final String KNOWN_OPT_2 = "opt2";

        /**
         * Kknown namespace.
         */
        public static final String KNOWN_NAMESPACE = "com.bosch";

        /**
         * Kknown namespaces set.
         */
        public static final Set<String> KNOWN_NAMESPACES_SET = Collections.singleton(KNOWN_NAMESPACE);

        /**
         * A known message for {@code InvalidFilterException}.
         */
        public static final String KNOWN_INVALID_FILTER_EXCEPTION_MESSAGE = "Invalid filter: " + KNOWN_FILTER_STR;
        /**
         * A known {@code InvalidFilterException}.
         */
        public static final InvalidFilterException INVALID_FILTER_EXCEPTION =
                InvalidFilterException
                        .newBuilder().message(KNOWN_INVALID_FILTER_EXCEPTION_MESSAGE).build();

        /**
         * A known message for {@code InvalidOptionException}.
         */
        public static final String KNOWN_INVALID_OPTION_EXCEPTION_MESSAGE = "Invalid option: " + KNOWN_OPT_1;
        /**
         * A known {@code InvalidOptionException}.
         */
        public static final InvalidOptionException INVALID_OPTION_EXCEPTION =
                InvalidOptionException
                        .newBuilder().message(KNOWN_INVALID_OPTION_EXCEPTION_MESSAGE).build();
    }

}
