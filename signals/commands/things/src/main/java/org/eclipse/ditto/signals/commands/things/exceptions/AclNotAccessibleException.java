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
package org.eclipse.ditto.signals.commands.things.exceptions;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingException;

/**
 * This exception indicates that the ACL of a Thing cannot be accessed by a particular Authorization Subject because the
 * Thing could not be found or the subject has insufficient permissions.
 */
@Immutable
public final class AclNotAccessibleException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "acl.notfound";

    private static final String MESSAGE_TEMPLATE =
            "The ACL Entry for the Authorization Subject ''{0}'' on the Thing " + "with ID ''{1}'' could not be found.";


    private static final String DEFAULT_DESCRIPTION =
            "Check if the ID of the Thing and the authorized subject of your request was correct.";

    private static final long serialVersionUID = 6811033520675841466L;

    private AclNotAccessibleException(final DittoHeaders dittoHeaders, final String message,
            final String description, final Throwable cause, final URI href) {
        super(ERROR_CODE, HttpStatusCode.NOT_FOUND, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code AclNotAccessibleException}.
     *
     * @param thingId the ID of the Thing.
     * @param authorizationSubject the Authorization Subject whose ACL entry is either not present on the Thing or the
     * requester had insufficient access permissions.
     * @return the builder.
     */
    public static Builder newBuilder(final String thingId, final AuthorizationSubject authorizationSubject) {
        return new Builder(thingId, authorizationSubject);
    }

    /**
     * Constructs a new {@code AclNotAccessibleException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new AclNotAccessibleException.
     */
    public static AclNotAccessibleException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code AclNotAccessibleException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new AclNotAccessibleException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static AclNotAccessibleException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link AclNotAccessibleException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<AclNotAccessibleException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String thingId, final AuthorizationSubject authorizationSubject) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, authorizationSubject, thingId));
        }

        @Override
        protected AclNotAccessibleException doBuild(final DittoHeaders dittoHeaders, final String message,
                final String description, final Throwable cause, final URI href) {
            return new AclNotAccessibleException(dittoHeaders, message, description, cause, href);
        }
    }

}
