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
import java.util.Set;
import java.util.stream.Collectors;

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
 * This exception indicates that a request contains a reference placeholder which references an unsupported entity type.
 */
@Immutable
@JsonParsableException(errorCode = GatewayPlaceholderReferenceNotSupportedException.ERROR_CODE)
public final class GatewayPlaceholderReferenceNotSupportedException extends DittoRuntimeException
        implements GatewayException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "placeholder.reference.notsupported";

    private static final String MESSAGE_TEMPLATE = "The placeholder references a not supported entity: ''{0}''.";
    private static final String DESCRIPTION_TEMPLATE = "Please reference one of the supported entities: {0}.";
    private static final String DESCRIPTION_WITHOUT_SUPPORTED_ENTITIES =
            "Please reference one of the supported entities.";

    private static final long serialVersionUID = -8724860134957013912L;

    private GatewayPlaceholderReferenceNotSupportedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code GatewayPlaceholderReferenceNotSupportedException} for an unsupported referenced
     * entity type.
     *
     * @param unsupportedEntityType the unknown placeholder.
     * @param supportedEntityTypes the supported placeholders.
     * @return the builder.
     */
    public static Builder fromUnsupportedEntityType(final CharSequence unsupportedEntityType,
            final Set<CharSequence> supportedEntityTypes) {
        checkNotNull(unsupportedEntityType, "unsupportedEntityType");
        checkNotNull(supportedEntityTypes, "supportedEntityTypes");

        final String message = MessageFormat.format(MESSAGE_TEMPLATE, unsupportedEntityType);
        final String supportedEntitiesStr = supportedEntityTypes.stream()
                .map(supportedEntityType -> "'" + supportedEntityType + "'")
                .collect(Collectors.joining(", "));
        final String description = MessageFormat.format(DESCRIPTION_TEMPLATE, supportedEntitiesStr);

        return new Builder(message, description);
    }

    /**
     * Constructs a new {@code GatewayPlaceholderReferenceNotSupportedException} object with the exception message extracted
     * from the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new {@code GatewayPlaceholderReferenceNotSupportedException}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have
     * the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field.
     */
    public static GatewayPlaceholderReferenceNotSupportedException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DESCRIPTION_WITHOUT_SUPPORTED_ENTITIES))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@code GatewayPlaceholderReferenceNotSupportedException}.
     */
    @NotThreadSafe
    public static final class Builder
            extends DittoRuntimeExceptionBuilder<GatewayPlaceholderReferenceNotSupportedException> {

        private Builder() {}

        private Builder(final String message, final String description) {
            this();
            message(checkNotNull(message, "message"));
            description(checkNotNull(description, "description"));
        }

        @Override
        protected GatewayPlaceholderReferenceNotSupportedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message, @Nullable final String description, @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new GatewayPlaceholderReferenceNotSupportedException(dittoHeaders, message, description, cause,
                    href);
        }

    }

}
