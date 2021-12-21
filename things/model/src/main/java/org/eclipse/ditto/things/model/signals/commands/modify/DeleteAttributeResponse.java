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
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.AttributesModelFactory;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.AttributePointerInvalidException;

/**
 * Response to a {@link DeleteAttribute} command.
 */
@Immutable
@JsonParsableCommandResponse(type = DeleteAttributeResponse.TYPE)
public final class DeleteAttributeResponse extends AbstractCommandResponse<DeleteAttributeResponse>
        implements ThingModifyCommandResponse<DeleteAttributeResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + DeleteAttribute.NAME;

    static final JsonFieldDefinition<String> JSON_ATTRIBUTE =
            JsonFieldDefinition.ofString("attribute", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.NO_CONTENT;

    private static final CommandResponseJsonDeserializer<DeleteAttributeResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                JsonFactory.newPointer(jsonObject.getValueOrThrow(JSON_ATTRIBUTE)),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final JsonPointer attributePointer;

    private DeleteAttributeResponse(final ThingId thingId,
            final JsonPointer attributePointer,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.attributePointer = checkAttributePointer(attributePointer, dittoHeaders);
    }

    private static JsonPointer checkAttributePointer(final JsonPointer attributePointer,
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
     * Creates a response to a {@link DeleteAttribute} command.
     *
     * @param thingId the Thing ID of the deleted attribute.
     * @param attributePointer the JSON pointer of the deleted attribute.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.things.model.signals.commands.exceptions.AttributePointerInvalidException if
     * {@code attributePointer} is empty.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of {@code attributePointer} are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static DeleteAttributeResponse of(final ThingId thingId,
            final JsonPointer attributePointer,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, attributePointer, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code DeleteAttributeResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the attributes were deleted from.
     * @param attributePointer the JSON pointer of the deleted attribute.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code DeleteAttributeResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code DeleteAttributeResponse}.
     * @since 2.3.0
     */
    public static DeleteAttributeResponse newInstance(final ThingId thingId,
            final JsonPointer attributePointer,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new DeleteAttributeResponse(thingId,
                attributePointer,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        DeleteAttributeResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link DeleteAttribute} command from a JSON string.
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
    public static DeleteAttributeResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link DeleteAttribute} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of attribute pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static DeleteAttributeResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the JSON pointer of the deleted attribute.
     *
     * @return the pointer.
     */
    public JsonPointer getAttributePointer() {
        return attributePointer;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonFactory.newPointer("/attributes" + attributePointer);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_ATTRIBUTE, attributePointer.toString(), predicate);
    }

    @Override
    public DeleteAttributeResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, attributePointer, getHttpStatus(), dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DeleteAttributeResponse that = (DeleteAttributeResponse) o;
        return that.canEqual(this) &&
                super.equals(o) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(attributePointer, that.attributePointer);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof DeleteAttributeResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, attributePointer);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId
                + ", attributePointer=" + attributePointer + "]";
    }

}
