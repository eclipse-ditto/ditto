/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.signals.commands.modify;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link ModifyThingDefinition} command.
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyThingDefinitionResponse.TYPE)
public final class ModifyThingDefinitionResponse extends AbstractCommandResponse<ModifyThingDefinitionResponse>
        implements ThingModifyCommandResponse<ModifyThingDefinitionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyThingDefinition.NAME;

    static final JsonFieldDefinition<String> JSON_DEFINITION =
            JsonFieldDefinition.ofString("definition", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyThingDefinitionResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                jsonObject.getValue(JSON_DEFINITION)
                                        .map(ThingsModelFactory::newDefinition)
                                        .orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    @Nullable private final ThingDefinition definition;

    private ModifyThingDefinitionResponse(final ThingId thingId,
            @Nullable final ThingDefinition definition,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.definition = definition;
        if (HttpStatus.NO_CONTENT.equals(httpStatus) && null != definition) {
            throw new IllegalArgumentException(
                    MessageFormat.format("ThingDefinition <{0}> is illegal in conjunction with <{1}>.",
                            definition,
                            httpStatus)
            );
        }
    }

    /**
     * Returns a new {@code ModifyThingDefinitionResponse} for a created definition. This corresponds to the HTTP
     * status {@link org.eclipse.ditto.base.model.common.HttpStatus#CREATED}.
     *
     * @param thingId the Thing ID of the created definition.
     * @param definition the created definition.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created definition.
     * @throws NullPointerException if {@code thingId} or {@code dittoHeaders} is {@code null}.
     */
    public static ModifyThingDefinitionResponse created(final ThingId thingId,
            @Nullable final ThingDefinition definition,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, definition, HttpStatus.CREATED, dittoHeaders);
    }


    /**
     * Returns a new {@code ModifyThingDefinitionResponse} for a modified definition. This corresponds to the HTTP
     * status {@link org.eclipse.ditto.base.model.common.HttpStatus#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified definition.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified definition.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyThingDefinitionResponse modified(final ThingId thingId, final DittoHeaders dittoHeaders) {
        return newInstance(thingId, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyThingDefinitionResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the attribute belongs to.
     * @param httpStatus the status of the response.
     * @param definition the {@code ThingDefinition} that was created or {@code null} if an existing definition was
     * modified.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyThingDefinitionResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code ModifyThingDefinitionResponse}
     * or if {@code httpStatus} contradicts {@code definition}.
     * @since 2.3.0
     */
    public static ModifyThingDefinitionResponse newInstance(final ThingId thingId,
            @Nullable final ThingDefinition definition,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyThingDefinitionResponse(thingId,
                definition,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyThingDefinitionResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyThingDefinition} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyThingDefinitionResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyThingDefinition} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyThingDefinitionResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    /**
     * ModifyThingDefinitionResponse is only available in JsonSchemaVersion V_2.
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
     * Returns the created definition.
     *
     * @return the created definition.
     */
    public Optional<ThingDefinition> getDefinition() {
        return Optional.ofNullable(definition);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(definition).map(JsonValue::of);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of(Thing.JsonFields.DEFINITION.getPointer().toString());
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        if (definition != null) {
            jsonObjectBuilder.set(JSON_DEFINITION, String.valueOf(definition), predicate);
        }
    }

    @Override
    public ModifyThingDefinitionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, definition, getHttpStatus(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyThingDefinitionResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyThingDefinitionResponse that = (ModifyThingDefinitionResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(definition, that.definition) &&
                super.equals(o);
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
