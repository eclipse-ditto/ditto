/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.batch.exceptions;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.WithId;

/**
 * Thrown if a batch command cannot be executed by the coordinator.
 */
@Immutable
public final class BatchNotExecutableException extends DittoRuntimeException implements BatchException, WithId {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "batch.notexecutable";

    private static final String MESSAGE_TEMPLATE =
            "The batch with ID ''{0}'' cannot be executed. At least one command of the batch failed.";

    private static final String DEFAULT_DESCRIPTION_TEMPLATE =
            "The command ''{0}'' failed with: {1}";

    private static final long serialVersionUID = 93438481409773848L;

    @Nullable private final String batchId;
    @Nullable private final String commandCorrelationId;

    private BatchNotExecutableException(@Nullable final String batchId,
            @Nullable final String commandCorrelationId,
            final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
        this.batchId = batchId;
        this.commandCorrelationId = commandCorrelationId;
    }

    /**
     * A mutable builder for a {@code BatchNotExecutableException}.
     *
     * @param batchId the ID of the batch.
     * @param commandCorrelationId the name of the failed command.
     * @param dittoRuntimeException the exception which was caused.
     * @return the builder.
     */
    public static Builder newBuilder(final String batchId, final String commandCorrelationId,
            final DittoRuntimeException dittoRuntimeException) {
        return new Builder(batchId, commandCorrelationId, dittoRuntimeException);
    }

    /**
     * Constructs a new {@code BatchNotExecutableException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new BatchNotExecutableException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static BatchNotExecutableException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code BatchNotExecutableException} object with the exception message extracted from the
     *
     * @param jsonObject the JSON to read the message,
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new BatchNotExecutableException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the message field.
     */
    public static BatchNotExecutableException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        final String batchId = jsonObject.getValue(JsonFields.BATCH_ID).orElse(null);
        final String commandCorrelationId = jsonObject.getValue(JsonFields.COMMAND_CORRELATION_ID).orElse(null);

        return new Builder()
                .batchId(batchId)
                .commandCorrelationId(commandCorrelationId)
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .build();
    }


    /**
     * Returns the identifier of the failed batch. If this exception is created from a message string, an empty
     * option is returned.
     *
     * @return the identifier.
     */
    private Optional<String> getBatchId() {
        return Optional.ofNullable(batchId);
    }

    @Nullable
    @Override
    public String getId() {
        return batchId;
    }

    /**
     * Returns the failed command's correlation id. If this exception is created from a message string, an empty
     * option is returned.
     *
     * @return the id.
     */
    public Optional<String> getCommandCorrelationId() {
        return Optional.ofNullable(commandCorrelationId);
    }

    /**
     * {@inheritDoc}
     * Appends the fields "batchId" and "commandCorrelationId".
     */
    @Override
    protected void appendToJson(final JsonObjectBuilder jsonObjectBuilder, final Predicate<JsonField> predicate) {
        jsonObjectBuilder
                .set(JsonFields.BATCH_ID, batchId, predicate)
                .set(JsonFields.COMMAND_CORRELATION_ID, commandCorrelationId, predicate);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) {
            return false;
        }
        final BatchNotExecutableException that = (BatchNotExecutableException) o;
        return Objects.equals(batchId, that.batchId) &&
                Objects.equals(commandCorrelationId, that.commandCorrelationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), batchId, commandCorrelationId);
    }

    @Immutable
    private static final class JsonFields {

        /**
         * JSON field containing the Batch ID.
         */
        static final JsonFieldDefinition<String> BATCH_ID =
                JsonFactory.newStringFieldDefinition("batchId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the command's correlation<String> ID.
         */
        static final JsonFieldDefinition<String> COMMAND_CORRELATION_ID =
                JsonFactory.newStringFieldDefinition("commandCorrelationId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    }

    /**
     * A mutable builder with a fluent API for a {@link BatchNotExecutableException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<BatchNotExecutableException> {

        @Nullable private String batchId;
        @Nullable private String commandCorrelationId;

        private Builder() {}

        private Builder(final String batchId, final String commandCorrelationId,
                final DittoRuntimeException dittoRuntimeException) {

            this.batchId = batchId;
            this.commandCorrelationId = commandCorrelationId;

            final String message = MessageFormat.format(MESSAGE_TEMPLATE, batchId);
            message(message);

            final String description = MessageFormat.format(DEFAULT_DESCRIPTION_TEMPLATE, commandCorrelationId,
                    dittoRuntimeException.toJsonString());
            description(description);
        }

        private Builder batchId(@Nullable final String batchId) {
            this.batchId = batchId;
            return this;
        }

        private Builder commandCorrelationId(@Nullable final String commandCorrelationId) {
            this.commandCorrelationId = commandCorrelationId;
            return this;
        }

        @Override
        protected BatchNotExecutableException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new BatchNotExecutableException(batchId, commandCorrelationId, dittoHeaders, message,
                    description, cause, href);
        }

    }

}
