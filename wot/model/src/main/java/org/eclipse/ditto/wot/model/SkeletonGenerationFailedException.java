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
package org.eclipse.ditto.wot.model;

import java.net.URI;
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
 * This exception indicates that the skeleton generation for a Thing failed.
 */
@Immutable
@JsonParsableException(errorCode = SkeletonGenerationFailedException.ERROR_CODE)
public final class SkeletonGenerationFailedException extends DittoRuntimeException implements WotException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "skeleton.generation.failed";

    private static final String MESSAGE_TEMPLATE = "Failed to generate a valid skeleton for the definition: ''{0}''.";
    private static final String DEFAULT_DESCRIPTION = "The provided ThingDefinition could not be used to generate a valid skeleton. Ensure the definition is correct and reachable.";


    private SkeletonGenerationFailedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code SkeletonGenerationFailedException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder(@Nullable final CharSequence definition) {
        return new Builder(definition);
    }

    /**
     * Constructs a new {@code SkeletonGenerationFailedException} object with given message.
     *
     * @param message detail message.
     * @param dittoHeaders the headers of the command that resulted in this exception.
     * @return the new SkeletonGenerationFailedException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static SkeletonGenerationFailedException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code SkeletonGenerationFailedException} object from JSON.
     *
     * @param jsonObject the JSON with the error message.
     * @param dittoHeaders the headers of the command that resulted in this exception.
     * @return the new SkeletonGenerationFailedException.
     */
    public static SkeletonGenerationFailedException fromJson(final JsonObject jsonObject,
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
     * A mutable builder for {@link SkeletonGenerationFailedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<SkeletonGenerationFailedException> {

        private Builder(@Nullable final CharSequence definition) {
            message(MESSAGE_TEMPLATE.replace("''{0}''", definition.toString()));
            description(DEFAULT_DESCRIPTION);
        }

        private Builder() {
            message(MESSAGE_TEMPLATE);
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected SkeletonGenerationFailedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new SkeletonGenerationFailedException(dittoHeaders, message, description, cause, href);
        }
    }
}
