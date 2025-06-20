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

import java.net.URI;
import java.text.MessageFormat;
import java.time.Instant;

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
 * Thrown if the history of a WoT validation config was not accessible.
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableException(errorCode = WotValidationConfigHistoryNotAccessibleException.ERROR_CODE)
public final class WotValidationConfigHistoryNotAccessibleException extends DittoRuntimeException implements
        WotValidationConfigException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "history.notfound";

    private static final String MESSAGE_TEMPLATE_REVISION =
            "The history of the WoT validation config with ID ''{0}'' and revision ''{1}'' was not accessible!";

    private static final String MESSAGE_TEMPLATE_TIMESTAMP =
            "The history of the WoT validation config with ID ''{0}'' at timestamp ''{1}'' was not accessible!";

    private static final String DEFAULT_DESCRIPTION =
            "Check if the ID and revision/timestamp of your requested WoT validation config history was correct.";

    private static final long serialVersionUID = -7806344741546228642L;

    private WotValidationConfigHistoryNotAccessibleException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code WotValidationConfigHistoryNotAccessibleException}.
     *
     * @param configId the ID of the WoT validation config.
     * @param revision the revision of the WoT validation config.
     * @return the builder.
     */
    public static Builder newBuilder(final WotValidationConfigId configId, final long revision) {
        return new Builder(configId, revision);
    }

    /**
     * A mutable builder for a {@code WotValidationConfigHistoryNotAccessibleException}.
     *
     * @param configId the ID of the WoT validation config.
     * @param timestamp the timestamp at which the WoT validation config was requested.
     * @return the builder.
     */
    public static Builder newBuilder(final WotValidationConfigId configId, final Instant timestamp) {
        return new Builder(configId, timestamp);
    }

    /**
     * Constructs a new {@code WotValidationConfigHistoryNotAccessibleException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new WotValidationConfigHistoryNotAccessibleException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static WotValidationConfigHistoryNotAccessibleException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code WotValidationConfigHistoryNotAccessibleException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new WotValidationConfigHistoryNotAccessibleException.
     */
    public static WotValidationConfigHistoryNotAccessibleException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link WotValidationConfigHistoryNotAccessibleException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<WotValidationConfigHistoryNotAccessibleException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final WotValidationConfigId configId, final long revision) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE_REVISION, String.valueOf(configId), String.valueOf(revision)));
        }

        private Builder(final WotValidationConfigId configId, final Instant timestamp) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE_TIMESTAMP, String.valueOf(configId), String.valueOf(timestamp)));
        }

        @Override
        protected WotValidationConfigHistoryNotAccessibleException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new WotValidationConfigHistoryNotAccessibleException(dittoHeaders, message, description, cause, href);
        }
    }
} 