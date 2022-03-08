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
package org.eclipse.ditto.things.model.signals.commands.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.FeatureToggle;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link RetrieveThing} command with {@link org.eclipse.ditto.base.model.headers.DittoHeaderDefinition#ACCEPT}
 * header being {@code "application/td+json"} requesting a WoT (Web of Things) Thing Description.
 *
 * @since 2.4.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveWotThingDescriptionResponse.TYPE)
public final class RetrieveWotThingDescriptionResponse
        extends AbstractCommandResponse<RetrieveWotThingDescriptionResponse>
        implements ThingQueryCommandResponse<RetrieveWotThingDescriptionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = ThingCommandResponse.TYPE_PREFIX + "retrieveWotThingDescription";

    static final JsonFieldDefinition<JsonObject> JSON_TD =
            JsonFieldDefinition.ofJsonObject("td", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_TD_PLAIN_JSON =
            JsonFieldDefinition.ofString("tdPlainJson", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveWotThingDescriptionResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();

                        final JsonObject tdJsonObject = jsonObject.getValue(JSON_TD).orElse(null);
                        final String tdPlainJsonString = jsonObject.getValue(JSON_TD_PLAIN_JSON)
                                .orElseGet(() -> {
                                    if (null == tdJsonObject) {
                                        throw JsonMissingFieldException.newBuilder()
                                                .fieldName(JSON_TD.getPointer())
                                                .build();
                                    }
                                    return tdJsonObject.toString();
                                });

                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                tdJsonObject,
                                tdPlainJsonString,
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final String tdPlainJson;

    @Nullable private JsonObject td;

    private RetrieveWotThingDescriptionResponse(final ThingId thingId,
            @Nullable final JsonObject td,
            final String tdPlainJson,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.tdPlainJson = checkNotNull(tdPlainJson, "tdPlainJson");
        this.td = td; // lazy init - might be null
    }

    /**
     * Creates a response to a {@link RetrieveThing} command returning the Thing Description (TD).
     *
     * @param thingId the Thing ID of the retrieved Thing.
     * @param td the retrieved+calculated Thing Description.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveWotThingDescriptionResponse of(final ThingId thingId,
            final JsonObject td,
            final DittoHeaders dittoHeaders) {

        checkNotNull(thingId, "thingId");
        return newInstance(thingId, td, td.toString(), HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThing} command returning the Thing Description (TD).
     *
     * @param thingId the Thing ID of the retrieved Thing.
     * @param tdPlainJson the retrieved+calculated Thing Description as plain JSON.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveWotThingDescriptionResponse of(final ThingId thingId, final String tdPlainJson,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, null, tdPlainJson, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code RetrieveThingResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing.
     * @param td the retrieved Thing Description or {@code null} if only {@code tdPlainJson} is available.
     * @param tdPlainJson the retrieved Thing Description as plain JSON string.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code RetrieveThingResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code RetrieveThingResponse}.
     */
    public static RetrieveWotThingDescriptionResponse newInstance(final ThingId thingId,
            @Nullable final JsonObject td,
            final String tdPlainJson,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        FeatureToggle.checkWotIntegrationFeatureEnabled(TYPE, dittoHeaders);

        return new RetrieveWotThingDescriptionResponse(thingId,
                td,
                tdPlainJson,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveWotThingDescriptionResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThing} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveWotThingDescriptionResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThing} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveWotThingDescriptionResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the retrieved Thing Description.
     *
     * @return the retrieved Thing Description.
     */
    public JsonObject getTd() {
        return lazyLoadTdJsonObject();
    }

    @Override
    public Optional<String> getEntityPlainString() {
        return Optional.of(tdPlainJson);
    }

    @Override
    public JsonObject getEntity(final JsonSchemaVersion schemaVersion) {
        return lazyLoadTdJsonObject();
    }

    private JsonObject lazyLoadTdJsonObject() {
        if (td == null) {
            td = JsonObject.of(tdPlainJson);
        }
        return td;
    }

    @Override
    public RetrieveWotThingDescriptionResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(thingId, entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrieveWotThingDescriptionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, td, tdPlainJson, getHttpStatus(), dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_TD_PLAIN_JSON, tdPlainJson, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveWotThingDescriptionResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveWotThingDescriptionResponse that = (RetrieveWotThingDescriptionResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(td, that.td) &&
                Objects.equals(tdPlainJson, that.tdPlainJson) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, td, tdPlainJson);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", td=" + td +
                ", tdPlainJson=" + tdPlainJson + "]";
    }

}
