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
package org.eclipse.ditto.services.models.things.commands.sudo;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link SudoRetrieveModifiedThingTags} command.
 */
@Immutable
public final class SudoRetrieveModifiedThingTagsResponse
        extends AbstractCommandResponse<SudoRetrieveModifiedThingTagsResponse>
        implements SudoCommandResponse<SudoRetrieveModifiedThingTagsResponse> {

    /**
     * Name of the response.
     */
    public static final String NAME = "sudoRetrieveModifiedThingTagsResponse";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonArray> JSON_MODIFIED_THING_TAGS =
            JsonFactory.newArrayFieldDefinition("payload/modifiedThingTags", FieldType.REGULAR,
                    JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

    private final JsonArray modifiedThingTags;

    private SudoRetrieveModifiedThingTagsResponse(final HttpStatusCode statusCode, final JsonArray modifiedThingTags,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.modifiedThingTags = checkNotNull(modifiedThingTags, "ThingTags");
    }

    /**
     * Creates a new instance of {@code SudoRetrieveModifiedThingTagsResponse}.
     *
     * @param thingTags the modified Things.
     * @param dittoHeaders the command headers of the request.
     * @return a new SudoRetrieveModifiedThingTagsResponse object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoRetrieveModifiedThingTagsResponse of(final JsonArray thingTags,
            final DittoHeaders dittoHeaders) {
        return new SudoRetrieveModifiedThingTagsResponse(HttpStatusCode.OK, thingTags, dittoHeaders);
    }

    /**
     * Creates a new instance of {@code SudoRetrieveModifiedThingTagsResponse}.
     *
     * @param thingTags the modified Things.
     * @param dittoHeaders the command headers of the request.
     * @return a new SudoRetrieveModifiedThingTagsResponse object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoRetrieveModifiedThingTagsResponse of(final List<ThingTag> thingTags,
            final DittoHeaders dittoHeaders) {
        return new SudoRetrieveModifiedThingTagsResponse(HttpStatusCode.OK,
                checkNotNull(thingTags, "ThingTags").stream() //
                        .map(ThingTag::toJson) //
                        .collect(JsonCollectors.valuesToArray()),
                dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveModifiedThingTagsResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which a new SudoRetrieveModifiedThingTagsResponse instance is to be
     * created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code SudoRetrieveModifiedThingTagsResponse} which was created from the given JSON string.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     * @throws DittoJsonException if the passed in {@code jsonString} was {@code null}, empty or not in the expected
     * 'SudoRetrieveModifiedThingTagsResponse' format.
     */
    public static SudoRetrieveModifiedThingTagsResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return fromJson(jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveModifiedThingTagsResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new SudoRetrieveModifiedThingTagsResponse instance is to be
     * created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code SudoRetrieveModifiedThingTagsResponse} which was created from the given JSON object.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'SudoRetrieveModifiedThingTagsResponse' format.
     */
    public static SudoRetrieveModifiedThingTagsResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<SudoRetrieveModifiedThingTagsResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final JsonArray thingTagsJsonArray = jsonObject.getValueOrThrow(JSON_MODIFIED_THING_TAGS);
                    return of(thingTagsJsonArray, dittoHeaders);
                });
    }

    /**
     * Returns the modified ThingTags.
     *
     * @return the modified ThingTags.
     */
    public List<ThingTag> getModifiedThingTags() {
        return modifiedThingTags.stream().filter(JsonValue::isObject).map(JsonValue::asObject).map(ThingTag::fromJson)
                .collect(Collectors.toList());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return modifiedThingTags;
    }

    @Override
    public SudoRetrieveModifiedThingTagsResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(entity.asArray(), getDittoHeaders());
    }

    @Override
    public SudoRetrieveModifiedThingTagsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(modifiedThingTags, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_MODIFIED_THING_TAGS, modifiedThingTags, predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), modifiedThingTags);
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
        final SudoRetrieveModifiedThingTagsResponse that = (SudoRetrieveModifiedThingTagsResponse) o;
        return that.canEqual(this) && Objects.equals(modifiedThingTags, that.modifiedThingTags) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoRetrieveModifiedThingTagsResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", modifiedThingTags=" + modifiedThingTags + "]";
    }

}
