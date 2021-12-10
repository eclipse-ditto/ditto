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
import org.eclipse.ditto.things.model.AttributesModelFactory;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.AttributePointerInvalidException;

/**
 * Response to a {@link ModifyAttribute} command.
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyAttributeResponse.TYPE)
public final class ModifyAttributeResponse extends AbstractCommandResponse<ModifyAttributeResponse>
        implements ThingModifyCommandResponse<ModifyAttributeResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = ThingCommandResponse.TYPE_PREFIX + ModifyAttribute.NAME;

    static final JsonFieldDefinition<String> JSON_ATTRIBUTE =
            JsonFieldDefinition.ofString("attribute", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_VALUE =
            JsonFieldDefinition.ofJsonValue("value", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyAttributeResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                JsonPointer.of(jsonObject.getValueOrThrow(JSON_ATTRIBUTE)),
                                jsonObject.getValue(JSON_VALUE).orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final JsonPointer attributePointer;
    @Nullable private final JsonValue attributeValue;

    private ModifyAttributeResponse(final ThingId thingId,
            final JsonPointer attributePointer,
            @Nullable final JsonValue attributeValue,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.attributePointer = validateAttributePointer(attributePointer, dittoHeaders);
        this.attributeValue = attributeValue;
        if (HttpStatus.NO_CONTENT.equals(httpStatus) && null != attributeValue) {
            throw new IllegalArgumentException(
                    MessageFormat.format("Attribute value <{0}> is illegal in conjunction with <{1}>.",
                            attributeValue,
                            httpStatus)
            );
        }
    }

    private static JsonPointer validateAttributePointer(final JsonPointer attributePointer,
            final DittoHeaders dittoHeaders) {

        checkNotNull(attributePointer, "attributePointer");
        if (attributePointer.isEmpty()) {
            throw AttributePointerInvalidException.newBuilder(attributePointer)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        return AttributesModelFactory.validateAttributePointer(attributePointer);
    }

    /**
     * Returns a new {@code ModifyAttributeResponse} for a created Attribute. This corresponds to the HTTP status
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#CREATED}.
     *
     * @param thingId the Thing ID of the created attribute.
     * @param attributePointer the pointer of the created Attribute.
     * @param attributeValue the created Attribute value.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created FeatureProperties.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.things.model.signals.commands.exceptions.AttributePointerInvalidException if
     * {@code attributePointer} is empty.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of {@code attributePointer} are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static ModifyAttributeResponse created(final ThingId thingId,
            final JsonPointer attributePointer,
            final JsonValue attributeValue,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId,
                attributePointer,
                checkNotNull(attributeValue, "attributeValue"),
                HttpStatus.CREATED,
                dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyAttributeResponse} for a modified Attribute. This corresponds to the HTTP status
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified attribute.
     * @param attributePointer the pointer of the modified Attribute.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified FeatureProperties.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.things.model.signals.commands.exceptions.AttributePointerInvalidException if
     * {@code attributePointer} is empty.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of {@code attributePointer} are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static ModifyAttributeResponse modified(final ThingId thingId,
            final JsonPointer attributePointer,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, attributePointer, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyAttributeResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the attribute belongs to.
     * @param attributePointer the pointer of the attribute that is subject to the response.
     * @param attributeValue the created attribute value or {@code null} if an existing attribute was modified.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyAttributeResponse} instance.
     * @throws NullPointerException if any argument but {@code attributeValue} is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code ModifyAttributeResponse} or
     * if {@code httpStatus} contradicts {@code attributeValue}.
     * @since 2.3.0
     */
    public static ModifyAttributeResponse newInstance(final ThingId thingId,
            final JsonPointer attributePointer,
            @Nullable final JsonValue attributeValue,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyAttributeResponse(thingId,
                attributePointer,
                attributeValue,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyAttributeResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyAttribute} command from a JSON string.
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
    public static ModifyAttributeResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyAttribute} command from a JSON object.
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
    public static ModifyAttributeResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the pointer of the modified {@code Attribute}.
     *
     * @return the pointer of the modified Attribute.
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
        return JsonPointer.of("/attributes" + attributePointer);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_ATTRIBUTE, attributePointer.toString(), predicate);
        if (null != attributeValue) {
            jsonObjectBuilder.set(JSON_VALUE, attributeValue, predicate);
        }
    }

    @Override
    public ModifyAttributeResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, attributePointer, attributeValue, getHttpStatus(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyAttributeResponse;
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
