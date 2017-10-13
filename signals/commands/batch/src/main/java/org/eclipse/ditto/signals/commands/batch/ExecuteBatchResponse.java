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
package org.eclipse.ditto.signals.commands.batch;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link ExecuteBatch} command.
 */
@Immutable
public final class ExecuteBatchResponse extends AbstractCommandResponse<ExecuteBatchResponse> implements
        BatchCommandResponse<ExecuteBatchResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ExecuteBatch.NAME;

    private final String batchId;

    private ExecuteBatchResponse(final HttpStatusCode statusCode,
            final String batchId, final DittoHeaders dittoHeaders) {
        super(TYPE, statusCode, dittoHeaders);
        this.batchId = batchId;
    }

    /**
     * Returns a new {@code ExecuteBatchResponse} for a succeeded {@link ExecuteBatch}.
     *
     * @param batchId the identifier of the batch.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ExecuteBatchResponse of(final String batchId, final DittoHeaders dittoHeaders) {
        requireNonNull(batchId);
        requireNonNull(dittoHeaders);
        return new ExecuteBatchResponse(HttpStatusCode.OK, batchId, dittoHeaders);
    }

    /**
     * Creates a new {@code ExecuteBatchResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ExecuteBatchResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ExecuteBatchResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ExecuteBatchResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<ExecuteBatchResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String batchId = jsonObject.getValueOrThrow(BatchCommandResponse.JsonFields.BATCH_ID);
                    return of(batchId, dittoHeaders);
                });
    }

    @Override
    public String getBatchId() {
        return batchId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public ExecuteBatchResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(batchId, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(BatchCommandResponse.JsonFields.BATCH_ID, batchId, predicate);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ExecuteBatchResponse that = (ExecuteBatchResponse) o;
        return Objects.equals(batchId, that.batchId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), batchId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "batchId=" + batchId +
                "]";
    }
}
