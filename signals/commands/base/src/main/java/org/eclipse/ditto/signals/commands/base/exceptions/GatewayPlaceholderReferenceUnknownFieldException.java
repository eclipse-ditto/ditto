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
package org.eclipse.ditto.signals.commands.base.exceptions;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;

/**
 * This exception indicates that a request contains a reference placeholder which references an unknown field on the referenced entity.
 */
@Immutable
@JsonParsableException(errorCode = GatewayPlaceholderReferenceUnknownFieldException.ERROR_CODE)
public final class GatewayPlaceholderReferenceUnknownFieldException extends DittoRuntimeException
        implements GatewayException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "placeholder.reference.unknownfield";

    private static final String MESSAGE_TEMPLATE = "The referenced entity did not contain the expected field: ''{0}''.";
    private static final String DESCRIPTION_TEMPLATE =
            "Please verify that you specified a valid field of the referenced entity with id: {0}.";
    private static final String DESCRIPTION_WITHOUT_ENTITY_ID =
            "Please verify that you specified a valid field of the referenced entity.";

    private static final long serialVersionUID = 3721639494927413912L;

    private GatewayPlaceholderReferenceUnknownFieldException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code GatewayPlaceholderReferenceUnknownFieldException} for an unsupported referenced
     * entity type.
     *
     * @param unknownField the unknown field.
     * @param referencedEntityId the id of the referenced entity.
     * @return the builder.
     */
    public static Builder fromUnknownFieldAndEntityId(final CharSequence unknownField,
            final CharSequence referencedEntityId) {
        checkNotNull(unknownField, "unknownField");
        checkNotNull(referencedEntityId, "referencedEntityId");

        final String message = MessageFormat.format(MESSAGE_TEMPLATE, unknownField);
        final String description = MessageFormat.format(DESCRIPTION_TEMPLATE, referencedEntityId);

        return new Builder(message, description);
    }

    /**
     * Constructs a new {@code GatewayPlaceholderReferenceUnknownFieldException} object with the exception message extracted
     * from the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new {@code GatewayPlaceholderReferenceUnknownFieldException}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have
     * the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field.
     */
    public static GatewayPlaceholderReferenceUnknownFieldException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DESCRIPTION_WITHOUT_ENTITY_ID))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@code GatewayPlaceholderReferenceUnknownFieldException}.
     */
    @NotThreadSafe
    public static final class Builder
            extends DittoRuntimeExceptionBuilder<GatewayPlaceholderReferenceUnknownFieldException> {

        private Builder() {}

        private Builder(final String message, final String description) {
            this();
            message(checkNotNull(message, "message"));
            description(checkNotNull(description, "description"));
        }

        @Override
        protected GatewayPlaceholderReferenceUnknownFieldException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message, @Nullable final String description, @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new GatewayPlaceholderReferenceUnknownFieldException(dittoHeaders, message, description, cause,
                    href);
        }

    }

}
