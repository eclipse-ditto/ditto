/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.signals.commands.exceptions;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingException;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Thrown if a WoT validation config was not accessible.
 */
@JsonParsableException(errorCode = WotValidationConfigNotAccessibleException.ERROR_CODE)
public final class WotValidationConfigNotAccessibleException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ThingException.ERROR_CODE_PREFIX + "wot.validation.config.notaccessible";

    private static final String MESSAGE_TEMPLATE = "The WoT validation config with ID ''{0}'' was not accessible!";
    private static final String SCOPE_MESSAGE_TEMPLATE = "The dynamic config section with scope ''{0}'' was not found!";

    private static final String DEFAULT_DESCRIPTION = "Check if the ID of your requested config was correct.";
    private static final String SCOPE_DEFAULT_DESCRIPTION = "Check if the scope ID of your requested dynamic config section was correct.";

    private static final long serialVersionUID = -7806344741546228641L;

    private WotValidationConfigNotAccessibleException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code WotValidationConfigNotAccessibleException}.
     *
     * @param configId the ID of the WoT validation config.
     * @return the builder.
     */
    public static Builder newBuilder(final WotValidationConfigId configId) {
        return new Builder(configId);
    }

    /**
     * A mutable builder for a {@code WotValidationConfigNotAccessibleException} for scope-specific errors.
     *
     * @param scopeId the scope ID of the dynamic config section.
     * @return the builder.
     */
    public static Builder newBuilderForScope(final String scopeId) {
        return new Builder(scopeId);
    }

    /**
     * Constructs a new {@code WotValidationConfigNotAccessibleException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new WotValidationConfigNotAccessibleException.
     */
    public static WotValidationConfigNotAccessibleException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code WotValidationConfigNotAccessibleException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new WotValidationConfigNotAccessibleException.
     */
    public static WotValidationConfigNotAccessibleException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link WotValidationConfigNotAccessibleException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<WotValidationConfigNotAccessibleException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final WotValidationConfigId configId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(configId)));
        }

        private Builder(final String scopeId) {
            description(SCOPE_DEFAULT_DESCRIPTION);
            message(MessageFormat.format(SCOPE_MESSAGE_TEMPLATE, scopeId));
        }

        @Override
        protected WotValidationConfigNotAccessibleException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new WotValidationConfigNotAccessibleException(dittoHeaders, message, description, cause, href);
        }
    }
} 