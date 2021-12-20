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
package org.eclipse.ditto.thingsearch.model.signals.commands.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;

/**
 * Response to a {@link CountThings} command.
 */
@Immutable
@JsonParsableCommandResponse(type = CountThingsResponse.TYPE)
public final class CountThingsResponse extends AbstractCommandResponse<CountThingsResponse>
        implements ThingSearchQueryCommandResponse<CountThingsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + CountThings.NAME;

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<CountThingsResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        final JsonValue jsonValue = jsonObject.getValueOrThrow(JsonFields.PAYLOAD);
                        if (jsonValue.isLong()) {
                            return new CountThingsResponse(jsonValue.asLong(),
                                    context.getDeserializedHttpStatus(),
                                    context.getDittoHeaders());
                        } else {
                            throw new JsonParseException(MessageFormat.format(
                                    "Payload JSON value <{0}> is not a count representation!",
                                    jsonValue
                            ));
                        }
                    });

    private final long count;

    private CountThingsResponse(final long count, final HttpStatus httpStatus, final DittoHeaders dittoHeaders) {
        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        CountThingsResponse.class),
                dittoHeaders);
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
        return new CountThingsResponse(count, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a CountThingsResponse command from a JSON string.
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
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code CountThingsResponse} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the command which caused this response.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CountThingsResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
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
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.PAYLOAD, JsonValue.of(count), predicate);
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
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof CountThingsResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", count=" + count + "]";
    }

}
