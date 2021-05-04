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
package org.eclipse.ditto.thingsearch.model.signals.commands.query;

import java.util.Collections;
import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.thingsearch.model.signals.commands.exceptions.InvalidOptionException;

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
            .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AuthorizationSubject.newInstance("the_subject"),
                    AuthorizationSubject.newInstance("another_subject")))
            .build();

    /**
     * Empty command headers.
     */
    public static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    /**
     * Known JSON parse options.
     */
    public static final JsonParseOptions JSON_PARSE_OPTIONS =
            JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();

    /**
     * A known filter string.
     */
    public static final String KNOWN_FILTER_STR = "eq(thingId,4711)";

    /**
     * A known option.
     */
    public static final String KNOWN_OPT_1 = "opt1";

    /**
     * A known message for {@code InvalidOptionException}.
     */
    public static final String KNOWN_INVALID_OPTION_EXCEPTION_MESSAGE = "Invalid option: " + KNOWN_OPT_1;

    /**
     * A known {@code InvalidOptionException}.
     */
    public static final InvalidOptionException INVALID_OPTION_EXCEPTION = InvalidOptionException.newBuilder()
            .message(KNOWN_INVALID_OPTION_EXCEPTION_MESSAGE)
            .build();

    /**
     * Another known option.
     */
    public static final String KNOWN_OPT_2 = "opt2";

    /**
     * Known namespace.
     */
    public static final String KNOWN_NAMESPACE = "com.bosch";

    /**
     * A cursor.
     */
    public static final String CURSOR = "next-page-cursor";

    /**
     * Known namespaces set.
     */
    public static final Set<String> KNOWN_NAMESPACES_SET = Collections.singleton(KNOWN_NAMESPACE);

    private TestConstants() {
        throw new AssertionError();
    }

}
