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
package org.eclipse.ditto.things.api.commands.sudo;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingsModelFactory;

/**
 * Response to a {@link SudoRetrieveThing} command.
 */
@Immutable
@JsonParsableCommandResponse(type = SudoRetrieveThingResponse.TYPE)
public final class SudoRetrieveThingResponse extends AbstractCommandResponse<SudoRetrieveThingResponse>
        implements SudoCommandResponse<SudoRetrieveThingResponse> {

    /**
     * Name of the response.
     */
    public static final String NAME = "sudoRetrieveThingResponse";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_THING =
            JsonFactory.newJsonObjectFieldDefinition("payload/thing", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private final JsonObject thing;

    private SudoRetrieveThingResponse(final HttpStatus httpStatus, final JsonObject thing,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thing = checkNotNull(thing, "Thing");
    }

    /**
     * Creates a new instance of {@code SudoRetrieveThingResponse}.
     *
     * @param thing the retrieved Thing.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoRetrieveThingResponse of(final JsonObject thing, final DittoHeaders dittoHeaders) {
        return new SudoRetrieveThingResponse(HttpStatus.OK, thing, dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveThingResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which a new SudoRetrieveThingResponse instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code SudoRetrieveThingResponse} which was created from the given JSON string.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws DittoJsonException if the passed in {@code jsonString} was {@code null}, empty or not in the expected
     * 'SudoRetrieveThingResponse' format.
     */
    public static SudoRetrieveThingResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        final var jsonObject = DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return fromJson(jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveThingResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new SudoRetrieveThingResponse instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code SudoRetrieveThingResponse} which was created from the given JSON object.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'SudoRetrieveThingResponse' format.
     */
    public static SudoRetrieveThingResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<SudoRetrieveThingResponse>(TYPE, jsonObject).deserialize(
                httpStatus -> of(jsonObject.getValueOrThrow(JSON_THING), dittoHeaders));
    }

    /**
     * Returns the {@code Thing}.
     *
     * @return the Thing.
     */
    public Thing getThing() {
        return ThingsModelFactory.newThing(thing);
    }

    @Override
    public JsonObject getEntity(final JsonSchemaVersion schemaVersion) {
        return thing;
    }

    @Override
    public SudoRetrieveThingResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(entity.asObject(), getDittoHeaders());
    }

    @Override
    public SudoRetrieveThingResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thing, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_THING, thing, predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thing);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "pmd:SimplifyConditional"})
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SudoRetrieveThingResponse that = (SudoRetrieveThingResponse) o;
        return that.canEqual(this) && Objects.equals(thing, that.thing) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoRetrieveThingResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thing=" + thing + "]";
    }

}
