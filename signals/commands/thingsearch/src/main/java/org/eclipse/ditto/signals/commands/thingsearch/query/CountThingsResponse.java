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
package org.eclipse.ditto.signals.commands.thingsearch.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link CountThings} command.
 */
@Immutable
public final class CountThingsResponse extends AbstractCommandResponse<CountThingsResponse>
        implements ThingSearchQueryCommandResponse<CountThingsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + CountThings.NAME;

    private final long count;

    private CountThingsResponse(final long count, final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);
        this.count = count;
    }

    /**
     * Returns a new {@code CountThingsResponse} instance for the issued query.
     *
     * @param count the number of Things which was retrieved from the Search service.
     * @param dittoHeaders the headers of the command which caused this response.
     * @return a new response for the "Count Things" command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CountThingsResponse of(final long count, final DittoHeaders dittoHeaders) {
        return new CountThingsResponse(count, dittoHeaders);
    }

    /**
     * Creates a response to a {@link CountThingsResponse} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the command which caused this response.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static CountThingsResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link CountThingsResponse} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the command which caused this response.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CountThingsResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<CountThingsResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final JsonValue count = jsonObject.getValueOrThrow(JsonFields.PAYLOAD);

                    return of(count.asLong(), dittoHeaders);
                });
    }

    /**
     * Returns the count.
     *
     * @return the count.
     */
    public long getCount() {
        return count;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return JsonValue.of(count);
    }

    @Override
    public CountThingsResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(entity.asInt(), getDittoHeaders());
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.PAYLOAD, JsonFactory.newValue(count), predicate);
    }

    @Override
    public CountThingsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(count, dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), count);
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
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
        final CountThingsResponse that = (CountThingsResponse) o;
        return that.canEqual(this) && Objects.equals(count, that.count) && super.equals(that);
    }

    @Override
    protected boolean canEqual(final Object other) {
        return (other instanceof CountThingsResponse);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", count=" + count + "]";
    }
}
