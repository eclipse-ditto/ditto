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
package org.eclipse.ditto.things.model.devops.exceptions;

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
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Thrown if the WoT validation config was either not present in Ditto at all or if the requester had insufficient
 * permissions to access it.
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableException(errorCode = WotValidationConfigNotAccessibleException.ERROR_CODE)
public final class WotValidationConfigNotAccessibleException extends DittoRuntimeException implements
        WotValidationConfigException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "notaccessible";

    private static final String MESSAGE_TEMPLATE =
            "The WoT validation config with ID ''{0}'' could not be found or requester had insufficient permissions to access it.";

    private static final String DEFAULT_DESCRIPTION =
            "Check if the ID of your requested WoT validation config was correct and you have sufficient permissions.";

    private static final String SCOPE_DEFAULT_DESCRIPTION = "Check if the scope ID of your requested dynamic config section was correct.";

    private static final String SCOPE_MESSAGE_TEMPLATE = "The dynamic config section with scope ''{0}'' was not found!";

    private static final long serialVersionUID = -623037881914361095L;

    private WotValidationConfigNotAccessibleException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, dittoHeaders, message, description, cause, href);
    }

    /**
     * Constructs a new {@code WotValidationConfigNotAccessibleException} object.
     *
     * @param configId the ID of the WoT validation config which is not accessible.
     * @param dittoHeaders the headers with which this Exception should be reported back to the user.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public WotValidationConfigNotAccessibleException(final WotValidationConfigId configId,
            final DittoHeaders dittoHeaders) {
        this(dittoHeaders, getMessage(configId), DEFAULT_DESCRIPTION, null, null);
    }

    private static String getMessage(final WotValidationConfigId configId) {
        checkNotNull(configId, "configId");
        return MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(configId));
    }

    /**
     * A mutable builder for a {@code WotValidationConfigNotAccessibleException}.
     *
     * @param configId the ID of the WoT validation config.
     * @return the builder.
     * @throws NullPointerException if {@code configId} is {@code null}.
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
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static WotValidationConfigNotAccessibleException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code WotValidationConfigNotAccessibleException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new WotValidationConfigNotAccessibleException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
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
            message(WotValidationConfigNotAccessibleException.getMessage(configId));
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