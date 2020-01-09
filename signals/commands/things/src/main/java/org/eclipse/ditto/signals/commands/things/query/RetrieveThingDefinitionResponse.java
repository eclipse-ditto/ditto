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
package org.eclipse.ditto.signals.commands.things.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingDefinition;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;

/**
 * Response to a {@link org.eclipse.ditto.signals.commands.things.query.RetrieveThingDefinition} command.
 */

@Immutable
@JsonParsableCommandResponse(type = RetrieveThingDefinitionResponse.TYPE)
public final class RetrieveThingDefinitionResponse extends AbstractCommandResponse<RetrieveThingDefinitionResponse>
        implements
        ThingQueryCommandResponse<RetrieveThingDefinitionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveThingDefinition.NAME;

    static final JsonFieldDefinition<JsonValue> JSON_DEFINITION =
            JsonFactory.newJsonValueFieldDefinition("definition", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final ThingId thingId;
    private final ThingDefinition definition;

    private RetrieveThingDefinitionResponse(final ThingId thingId, final HttpStatusCode statusCode,
            final ThingDefinition definition, final DittoHeaders dittoHeaders) {
        super(TYPE, statusCode, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thing ID");
        this.definition = checkNotNull(definition, "definition is null");
    }


    /**
     * Creates a response to a {@link org.eclipse.ditto.signals.commands.things.query.RetrieveThingDefinition} command.
     *
     * @param thingId the Thing ID of the retrieved Definition.
     * @param definition the retrieved Definition.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveThingDefinitionResponse of(final ThingId thingId, final ThingDefinition definition,
            final DittoHeaders dittoHeaders) {
        return new RetrieveThingDefinitionResponse(thingId, HttpStatusCode.OK, definition, dittoHeaders);
    }

    /**
     * Creates a response to a {@link org.eclipse.ditto.signals.commands.things.query.RetrieveThingDefinition}
     * command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveThingDefinitionResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link org.eclipse.ditto.signals.commands.things.query.RetrieveThingDefinition} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveThingDefinitionResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<RetrieveThingDefinitionResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String extractedThingId =
                            jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID);
                    final ThingId thingId = ThingId.of(extractedThingId);
                    final JsonValue readDefinition = jsonObject.getValueOrThrow(JSON_DEFINITION);
                    final ThingDefinition definition;
                    if (readDefinition.isNull()) {
                        definition = ThingsModelFactory.nullDefinition();
                    } else {
                        definition = ThingsModelFactory.newDefinition(readDefinition.asString());
                    }

                    return of(thingId, definition, dittoHeaders);
                });
    }

    /**
     * RetrieveThingDefinitionResponse is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions.
     */
    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    @Override
    public ThingId getThingEntityId() {
        return thingId;
    }


    /**
     * Returns the retrieved Definition.
     *
     * @return the retrieved Definition.
     */
    public ThingDefinition getThingDefinition() {
        return definition;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return JsonValue.of(definition);
    }

    @Override
    public RetrieveThingDefinitionResponse setEntity(final JsonValue entity) {
        if (entity.asString().isEmpty()){
            return  of(thingId, ThingsModelFactory.nullDefinition(), getDittoHeaders());
        }
        return of(thingId, ThingsModelFactory.newDefinition(entity.asString()), getDittoHeaders());
    }

    @Override
    public RetrieveThingDefinitionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, definition, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/definition");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, String.valueOf(thingId), predicate);
        if (definition.equals(ThingsModelFactory.nullDefinition())) {
            jsonObjectBuilder.set(JSON_DEFINITION, JsonValue.nullLiteral(), predicate);
        } else {
            jsonObjectBuilder.set(JSON_DEFINITION, JsonValue.of(String.valueOf(definition)), predicate);
        }
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof RetrieveThingDefinitionResponse);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveThingDefinitionResponse that = (RetrieveThingDefinitionResponse) o;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(definition, that.definition) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, definition);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", definition=" +
                definition + "]";
    }

}
