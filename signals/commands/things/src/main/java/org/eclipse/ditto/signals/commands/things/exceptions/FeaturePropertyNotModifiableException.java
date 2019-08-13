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
package org.eclipse.ditto.signals.commands.things.exceptions;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;
import org.eclipse.ditto.model.things.ThingException;
import org.eclipse.ditto.model.things.ThingId;

/**
 * Indicates that the feature property cannot be modified.
 */
@JsonParsableException(errorCode = FeaturePropertyNotModifiableException.ERROR_CODE)
public class FeaturePropertyNotModifiableException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "feature.property.notmodifiable";

    private static final String MESSAGE_TEMPLATE = "The Property with JSON Pointer ''{0}'' of the Feature with"
            +
            " ID ''{1}'' on the Thing with ID ''{2}'' could not be modified as the requester had insufficient permissions"
            + " to modify it ('WRITE' is required).";

    private static final String DEFAULT_DESCRIPTION =
            "Check if the ID of the Thing, the Feature ID and the key of your requested property"
                    + " was correct and you have sufficient permissions.";

    private static final long serialVersionUID = -1793783227991859251L;

    private FeaturePropertyNotModifiableException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.FORBIDDEN, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code FeaturePropertyNotModifiableException}.
     *
     * @param thingId the ID of the Thing.
     * @param featureId the ID of the Feature.
     * @param jsonPointer the JSON Pointer of the Property.
     * @return the builder.
     */
    public static FeaturePropertyNotModifiableException.Builder newBuilder(final ThingId thingId, final String featureId,
            final JsonPointer jsonPointer) {
        return new FeaturePropertyNotModifiableException.Builder(thingId, featureId, jsonPointer);
    }

    /**
     * Constructs a new {@code FeaturePropertyNotModifiableException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new FeaturePropertyNotModifiableException.
     */
    public static FeaturePropertyNotModifiableException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {
        return new FeaturePropertyNotModifiableException.Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code FeaturePropertyNotModifiableException} object with the exception message extracted from
     * the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new FeaturePropertyNotModifiableException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static FeaturePropertyNotModifiableException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DEFAULT_DESCRIPTION))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link FeaturePropertyNotModifiableException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<FeaturePropertyNotModifiableException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final ThingId thingId, final String featureId, final JsonPointer jsonPointer) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, jsonPointer, featureId, String.valueOf(thingId)));
        }

        @Override
        protected FeaturePropertyNotModifiableException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new FeaturePropertyNotModifiableException(dittoHeaders, message, description, cause, href);
        }
    }

}
