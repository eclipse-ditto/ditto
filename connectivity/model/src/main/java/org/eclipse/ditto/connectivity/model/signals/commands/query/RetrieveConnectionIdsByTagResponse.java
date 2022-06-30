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
package org.eclipse.ditto.connectivity.model.signals.commands.query;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Response to a {@link RetrieveConnectionIdsByTag} command.
 *
 * @since 3.0.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveConnectionIdsByTagResponse.TYPE)
public final class RetrieveConnectionIdsByTagResponse
        extends AbstractCommandResponse<RetrieveConnectionIdsByTagResponse>
        implements ConnectivityQueryCommandResponse<RetrieveConnectionIdsByTagResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveConnectionIdsByTag.NAME;

    static final JsonFieldDefinition<JsonArray> CONNECTION_IDS =
            JsonFieldDefinition.ofJsonArray("connectionIds", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveConnectionIdsByTagResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new RetrieveConnectionIdsByTagResponse(
                                fromArray(jsonObject.getValueOrThrow(CONNECTION_IDS)),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders());
                    });

    private final Set<ConnectionId> connectionIds;

    private RetrieveConnectionIdsByTagResponse(final Set<ConnectionId> connectionIds,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveConnectionIdsByTagResponse.class),
                dittoHeaders);
        this.connectionIds = Collections.unmodifiableSet(new LinkedHashSet<>(connectionIds));
    }

    /**
     * Returns a new instance of {@code RetrieveConnectionIdsByTagResponse}.
     *
     * @param dittoHeaders the headers of the request.
     * @param connectionIds the connection ids.
     * @return a new RetrieveAllConnectionIdsResponse response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionIdsByTagResponse of(final Set<ConnectionId> connectionIds,
            final DittoHeaders dittoHeaders) {

        return new RetrieveConnectionIdsByTagResponse(connectionIds, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnectionIdsByTagResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be retrieved.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveConnectionIdsByTagResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnectionIdsByTagResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveConnectionIdsByTagResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(CONNECTION_IDS, JsonArray.of(connectionIds), predicate);
    }

    public Set<ConnectionId> getConnectionIds() {
        return connectionIds;
    }

    @Override
    public RetrieveConnectionIdsByTagResponse setEntity(final JsonValue entity) {
        return of(fromArray(entity.asArray()), getDittoHeaders());
    }

    private static Set<ConnectionId> fromArray(final JsonArray array) {
        return array.stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(ConnectionId::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return JsonArray.of(connectionIds);
    }

    @Override
    public RetrieveConnectionIdsByTagResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionIds, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveConnectionIdsByTagResponse;
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
        final RetrieveConnectionIdsByTagResponse that = (RetrieveConnectionIdsByTagResponse) o;
        return Objects.equals(connectionIds, that.connectionIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionIds);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", connectionIds=" + connectionIds +
                "]";
    }

}
