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
package org.eclipse.ditto.model.messages;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Thrown if a claim message was sent too many times for a single authorization subject.
 */
public final class AuthorizationSubjectBlockedException extends DittoRuntimeException implements MessageException {

    private static final long serialVersionUID = -5816231062202863122L;

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "authorization.blocked";

    private static final String MESSAGE_TEMPLATE =
            "Your Authorization Subject is temporarily blocked. Please try again later.";
    private static final String DEFAULT_DESCRIPTION =
            "You have sent too many claim requests at once. Please wait before trying again.";

    private AuthorizationSubjectBlockedException() {
        this(DittoHeaders.empty());
    }

    private AuthorizationSubjectBlockedException(final DittoHeaders dittoHeaders) {
        super(ERROR_CODE, HttpStatusCode.TOO_MANY_REQUESTS, dittoHeaders, MESSAGE_TEMPLATE, DEFAULT_DESCRIPTION,
                null, null);
    }

    /**
     * Creates an {@code AuthorizationSubjectBlockedException} object.
     *
     * @return The new exception object.
     */
    public static AuthorizationSubjectBlockedException newInstance() {
        return new AuthorizationSubjectBlockedException();
    }

    /**
     * Constructs a new {@code AuthorizationSubjectBlockedException} object with its fields extracted from
     * the given JSON object.
     *
     * @param json the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new exception object.
     */
    @SuppressWarnings("squid:S1172")
    public static AuthorizationSubjectBlockedException fromJson(final JsonObject json,
            final DittoHeaders dittoHeaders) {
        // ignore the JSON object.
        return new AuthorizationSubjectBlockedException(dittoHeaders);
    }

}
