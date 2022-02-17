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
import java.net.URL;
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
 * Thrown if a WoT (Web of Things) ThingModel could not be accessed (e.g. because the URL is not accessible or public
 * access to it is restricted).
 *
 * @since 2.4.0
 */
@Immutable
@JsonParsableException(errorCode = WotThingModelNotAccessibleException.ERROR_CODE)
public final class WotThingModelNotAccessibleException extends DittoRuntimeException implements WotException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "tm.notfound";

    private static final String MESSAGE_TEMPLATE =
            "The WoT ThingModel at URI ''{0}'' could not be accessed.";

    private static final String DEFAULT_DESCRIPTION =
            "Please ensure that the linked ThingModel is publicly available in order to be downloaded.";

    private static final long serialVersionUID = -8363529993633332681L;

    private WotThingModelNotAccessibleException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.MISDIRECTED_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * Constructs a new {@code WotThingModelNotAccessibleException} object.
     *
     * @param thingModelUrl the URL of the ThingModel which was not valid.
     * @param dittoHeaders the headers with which this Exception should be reported back to the user.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public WotThingModelNotAccessibleException(final URL thingModelUrl, final DittoHeaders dittoHeaders) {
        this(dittoHeaders, getMessage(thingModelUrl), DEFAULT_DESCRIPTION, null, null);
    }

    private static String getMessage(final URL thingModelUrl) {
        checkNotNull(thingModelUrl, "thingModelUrl");
        return MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(thingModelUrl));
    }

    /**
     * A mutable builder for a {@code WotThingModelNotAccessibleException}.
     *
     * @param thingModelUrl the URL of the ThingModel which was not valid.
     * @return the builder.
     * @throws NullPointerException if {@code thingId} is {@code null}.
     */
    public static Builder newBuilder(final URL thingModelUrl) {
        return new Builder(thingModelUrl);
    }

    /**
     * Constructs a new {@code WotThingModelNotAccessibleException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new WotThingModelNotAccessibleException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static WotThingModelNotAccessibleException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code WotThingModelNotAccessibleException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new WotThingModelNotAccessibleException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static WotThingModelNotAccessibleException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<WotThingModelNotAccessibleException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final URL thingModelUrl) {
            this();
            message(WotThingModelNotAccessibleException.getMessage(thingModelUrl));
        }

        @Override
        protected WotThingModelNotAccessibleException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new WotThingModelNotAccessibleException(dittoHeaders, message, description, cause, href);
        }

    }

}
