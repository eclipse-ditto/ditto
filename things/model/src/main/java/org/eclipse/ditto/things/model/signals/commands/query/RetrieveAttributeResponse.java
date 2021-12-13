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
import org.eclipse.ditto.things.model.AttributesModelFactory;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link RetrieveAttribute} command.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveAttributeResponse.TYPE)
public final class RetrieveAttributeResponse extends AbstractCommandResponse<RetrieveAttributeResponse>
        implements ThingQueryCommandResponse<RetrieveAttributeResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveAttribute.NAME;

    static final JsonFieldDefinition<String> JSON_ATTRIBUTE =
            JsonFieldDefinition.ofString("attribute", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_VALUE =
            JsonFieldDefinition.ofJsonValue("value", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveAttributeResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                JsonPointer.of(jsonObject.getValueOrThrow(JSON_ATTRIBUTE)),
                                jsonObject.getValueOrThrow(JSON_VALUE),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final JsonPointer attributePointer;
    private final JsonValue attributeValue;

    private RetrieveAttributeResponse(final ThingId thingId,
            final JsonPointer attributePointer,
            final JsonValue attributeValue,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.attributePointer = checkAttributePointer(attributePointer);
        this.attributeValue = checkNotNull(attributeValue, "attributeValue");
    }

    private static JsonPointer checkAttributePointer(final JsonPointer attributePointer) {
        return AttributesModelFactory.validateAttributePointer(checkNotNull(attributePointer, "attributePointer"));
    }

    /**
     * Creates a response to a {@link RetrieveAttribute} command.
     *
     * @param thingId the Thing ID of the retrieved attribute.
     * @param attributePointer the JSON pointer of the attribute to retrieve.
     * @param attributeValue the retrieved Attribute value.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of {@code attributePointer} are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static RetrieveAttributeResponse of(final ThingId thingId,
            final JsonPointer attributePointer,
            final JsonValue attributeValue,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, attributePointer, attributeValue, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code RetrieveAttributeResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the attribute belongs to.
     * @param attributePointer the JSON pointer of the attribute to retrieve.
     * @param attributeValue the retrieved Attribute value.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code RetrieveAttributeResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code RetrieveAttributeResponse}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of {@code attributePointer} are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     * @since 2.3.0
     */
    public static RetrieveAttributeResponse newInstance(final ThingId thingId,
            final JsonPointer attributePointer,
            final JsonValue attributeValue,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new RetrieveAttributeResponse(thingId,
                attributePointer,
                attributeValue,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveAttributeResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveAttribute} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of attribute pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static RetrieveAttributeResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveAttribute} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of attribute pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static RetrieveAttributeResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the retrieved Attribute.
     *
     * @return the retrieved Attribute.
     */
    public JsonValue getAttributeValue() {
        return attributeValue;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return attributeValue;
    }

    @Override
    public RetrieveAttributeResponse setEntity(final JsonValue entity) {
        return newInstance(thingId,
                attributePointer,
                checkNotNull(entity, "entity"),
                getHttpStatus(),
                getDittoHeaders());
    }

    @Override
    public RetrieveAttributeResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, attributePointer, attributeValue, getHttpStatus(), dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/attributes" + attributePointer);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_ATTRIBUTE, attributePointer.toString(), predicate);
        jsonObjectBuilder.set(JSON_VALUE, attributeValue, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveAttributeResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveAttributeResponse that = (RetrieveAttributeResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(attributePointer, that.attributePointer) &&
                Objects.equals(attributeValue, that.attributeValue) &&
                super.equals(o);
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
