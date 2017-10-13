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
package org.eclipse.ditto.signals.commands.things.modify;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
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
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link ModifyAttribute} command.
 */
@Immutable
public final class ModifyAttributeResponse extends AbstractCommandResponse<ModifyAttributeResponse> implements
        ThingModifyCommandResponse<ModifyAttributeResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyAttribute.NAME;

    static final JsonFieldDefinition<String> JSON_ATTRIBUTE =
            JsonFactory.newStringFieldDefinition("attribute", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_VALUE =
            JsonFactory.newJsonValueFieldDefinition("value", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    private final JsonPointer attributePointer;
    @Nullable private final JsonValue attributeValue;

    private ModifyAttributeResponse(final String thingId, final HttpStatusCode statusCode,
            final JsonPointer attributePointer,
            @Nullable final JsonValue attributeValue, final DittoHeaders dittoHeaders) {
        super(TYPE, statusCode, dittoHeaders);
        this.thingId = checkNotNull(thingId, "Thing ID");
        this.attributePointer = requireNonNull(attributePointer, "The Attribute Pointer must not be null!");
        this.attributeValue = attributeValue;
    }

    /**
     * Returns a new {@code ModifyAttributeResponse} for a created Attribute. This corresponds to the HTTP status code
     * {@link HttpStatusCode#CREATED}.
     *
     * @param thingId the Thing ID of the created attribute.
     * @param attributePointer the pointer of the created Attribute.
     * @param attributeValue the created Attribute value.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created FeatureProperties.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyAttributeResponse created(final String thingId, final JsonPointer attributePointer,
            final JsonValue attributeValue,
            final DittoHeaders dittoHeaders) {
        return new ModifyAttributeResponse(thingId, HttpStatusCode.CREATED, attributePointer, attributeValue,
                dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyAttributeResponse} for a modified Attribute. This corresponds to the HTTP status code
     * {@link HttpStatusCode#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified attribute.
     * @param attributePointer the pointer of the modified Attribute.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified FeatureProperties.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ModifyAttributeResponse modified(final String thingId, final JsonPointer attributePointer,
            final DittoHeaders dittoHeaders) {
        return new ModifyAttributeResponse(thingId, HttpStatusCode.NO_CONTENT, attributePointer, null, dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyAttribute} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyAttributeResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyAttribute} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyAttributeResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<ModifyAttributeResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String thingId =
                            jsonObject.getValueOrThrow(ThingModifyCommandResponse.JsonFields.JSON_THING_ID);
                    final String pointerString = jsonObject.getValueOrThrow(JSON_ATTRIBUTE);
                    final JsonPointer extractedAttributePointer = JsonFactory.newPointer(pointerString);
                    final JsonValue extractedValue = jsonObject.getValue(JSON_VALUE).orElse(null);

                    return new ModifyAttributeResponse(thingId, statusCode, extractedAttributePointer, extractedValue,
                            dittoHeaders);
                });
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    /**
     * Returns the pointer of the modified {@code Attribute}.
     *
     * @return the the pointer of the modified Attribute.
     */
    public JsonPointer getAttributePointer() {
        return attributePointer;
    }

    /**
     * Returns the created {@code Attribute}.
     *
     * @return the created {@code Attribute}.
     */
    public Optional<JsonValue> getAttributeValue() {
        return Optional.ofNullable(attributeValue);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(attributeValue);
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/attributes" + attributePointer;
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingModifyCommandResponse.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_ATTRIBUTE, attributePointer.toString(), predicate);
        if (null != attributeValue) {
            jsonObjectBuilder.set(JSON_VALUE, attributeValue, predicate);
        }
    }

    @Override
    public ModifyAttributeResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return (null != attributeValue) ?
                created(thingId, attributePointer, attributeValue, dittoHeaders)
                : modified(thingId, attributePointer, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ModifyAttributeResponse);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyAttributeResponse that = (ModifyAttributeResponse) o;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(attributePointer, that.attributePointer)
                && Objects.equals(attributeValue, that.attributeValue) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, attributePointer, attributeValue);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId
                + ", attributePointer=" + attributePointer + ", attributeValue=" + attributeValue + "]";
    }

}
