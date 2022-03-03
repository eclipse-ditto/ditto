/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.model;

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
 * Thrown if a WoT (Web of Things) ThingModel {@code tm:ref} was not in the specified format being:
 * {@code <uri>#<jsonPointer>}
 *
 * @since 2.4.0
 */
@Immutable
@JsonParsableException(errorCode = WotThingModelRefInvalidException.ERROR_CODE)
public final class WotThingModelRefInvalidException extends DittoRuntimeException implements WotException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "tm.ref.invalid";

    private static final String MESSAGE_TEMPLATE =
            "The WoT ThingModel tm:ref ''{0}'' is invalid.";

    private static final String DEFAULT_DESCRIPTION =
            "Please ensure that is in the specified format '<uri>#<jsonPointer>'.";

    private static final long serialVersionUID = 947845200833356146L;

    private WotThingModelRefInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * Constructs a new {@code WotThingModelRefInvalidException} object.
     *
     * @param tmRefValue the {@code tm:ref} value which was invalid.
     * @param dittoHeaders the headers with which this Exception should be reported back to the user.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public WotThingModelRefInvalidException(final CharSequence tmRefValue, final DittoHeaders dittoHeaders) {
        this(dittoHeaders, getMessage(tmRefValue), DEFAULT_DESCRIPTION, null, null);
    }

    private static String getMessage(final CharSequence tmRefValue) {
        checkNotNull(tmRefValue, "tmRefValue");
        return MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(tmRefValue));
    }

    /**
     * A mutable builder for a {@code WotThingModelRefInvalidException}.
     *
     * @param tmRefValue the {@code tm:ref} value which was invalid.
     * @return the builder.
     * @throws NullPointerException if {@code thingId} is {@code null}.
     */
    public static Builder newBuilder(final CharSequence tmRefValue) {
        return new Builder(tmRefValue);
    }

    /**
     * Constructs a new {@code WotThingModelRefInvalidException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new WotThingModelRefInvalidException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static WotThingModelRefInvalidException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code WotThingModelRefInvalidException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new WotThingModelRefInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static WotThingModelRefInvalidException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.wot.model.WotThingModelRefInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<WotThingModelRefInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final CharSequence tmRefValue) {
            this();
            message(WotThingModelRefInvalidException.getMessage(tmRefValue));
        }

        @Override
        protected WotThingModelRefInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new WotThingModelRefInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
