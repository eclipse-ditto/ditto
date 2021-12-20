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
package org.eclipse.ditto.things.model.signals.commands.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;

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
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link RetrieveThingDefinition} command.
 */

@Immutable
@JsonParsableCommandResponse(type = RetrieveThingDefinitionResponse.TYPE)
public final class RetrieveThingDefinitionResponse extends AbstractCommandResponse<RetrieveThingDefinitionResponse>
        implements ThingQueryCommandResponse<RetrieveThingDefinitionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveThingDefinition.NAME;

    static final JsonFieldDefinition<JsonValue> JSON_DEFINITION =
            JsonFieldDefinition.ofJsonValue("definition", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveThingDefinitionResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();

                        final JsonValue definitionJsonValue = jsonObject.getValueOrThrow(JSON_DEFINITION);

                        final ThingDefinition definition;
                        if (definitionJsonValue.isNull()) {
                            definition = ThingsModelFactory.nullDefinition();
                        } else {
                            definition = ThingsModelFactory.newDefinition(definitionJsonValue.asString());
                        }

                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                definition,
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final ThingDefinition definition;

    private RetrieveThingDefinitionResponse(final ThingId thingId,
            final ThingDefinition definition,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.definition = checkNotNull(definition, "definition");
    }

    /**
     * Creates a response to a {@link RetrieveThingDefinition} command.
     *
     * @param thingId the Thing ID of the retrieved Definition.
     * @param definition the retrieved Definition.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveThingDefinitionResponse of(final ThingId thingId,
            final ThingDefinition definition,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, definition, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code RetrieveThingDefinitionResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the definition belongs to.
     * @param definition the retrieved definition.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code RetrieveThingDefinitionResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a
     * {@code RetrieveThingDefinitionResponse}.
     * @since 2.3.0
     */
    public static RetrieveThingDefinitionResponse newInstance(final ThingId thingId,
            final ThingDefinition definition,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new RetrieveThingDefinitionResponse(thingId,
                definition,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveThingDefinitionResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThingDefinition}
     * command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveThingDefinitionResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThingDefinition} command
     * from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveThingDefinitionResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
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
    public ThingId getEntityId() {
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
        checkNotNull(entity, "entity");
        if (!entity.isString()) {
            throw new IllegalArgumentException(MessageFormat.format("Entity is not a JSON string but <{0}>.", entity));
        }
        final ThingDefinition thingDefinition;
        final String thingDefinitionString = entity.asString();
        if (thingDefinitionString.trim().isEmpty()) {
            thingDefinition = ThingsModelFactory.nullDefinition();
        } else {
            thingDefinition = ThingsModelFactory.newDefinition(thingDefinitionString);
        }
        return newInstance(thingId, thingDefinition, getHttpStatus(), getDittoHeaders());
    }

    @Override
    public RetrieveThingDefinitionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, definition, getHttpStatus(), dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/definition");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
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
        return other instanceof RetrieveThingDefinitionResponse;
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
        return that.canEqual(this) && Objects.equals(thingId, that.thingId) &&
                Objects.equals(definition, that.definition) && super.equals(o);
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
