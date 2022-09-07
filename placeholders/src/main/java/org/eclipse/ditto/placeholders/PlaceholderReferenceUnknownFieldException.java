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
package org.eclipse.ditto.placeholders;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;

/**
 * This exception indicates that a request contains a reference placeholder which references an unknown field on the referenced entity.
 */
@Immutable
@JsonParsableException(errorCode = PlaceholderReferenceUnknownFieldException.ERROR_CODE)
public final class PlaceholderReferenceUnknownFieldException extends DittoRuntimeException implements PlaceholderException {

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

    private PlaceholderReferenceUnknownFieldException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code PlaceholderReferenceUnknownFieldException} for an unsupported referenced
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
     * Constructs a new {@code PlaceholderReferenceUnknownFieldException} object with the exception message extracted
     * from the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new {@code PlaceholderReferenceUnknownFieldException}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static PlaceholderReferenceUnknownFieldException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder());
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new Builder()
                .message(getMessage())
                .description(getDescription().orElse(null))
                .cause(getCause())
                .href(getHref().orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@code PlaceholderReferenceUnknownFieldException}.
     */
    @NotThreadSafe
    public static final class Builder
            extends DittoRuntimeExceptionBuilder<PlaceholderReferenceUnknownFieldException> {

        private Builder() {description(DESCRIPTION_WITHOUT_ENTITY_ID);}

        private Builder(final String message, final String description) {
            this();
            message(checkNotNull(message, "message"));
            description(checkNotNull(description, "description"));
        }

        @Override
        protected PlaceholderReferenceUnknownFieldException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message, @Nullable final String description, @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new PlaceholderReferenceUnknownFieldException(dittoHeaders, message, description, cause,
                    href);
        }

    }

}
