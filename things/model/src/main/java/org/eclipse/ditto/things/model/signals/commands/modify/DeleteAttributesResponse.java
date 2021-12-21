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

import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link DeleteAttributes} command.
 */
@Immutable
@JsonParsableCommandResponse(type = DeleteAttributesResponse.TYPE)
public final class DeleteAttributesResponse extends AbstractCommandResponse<DeleteAttributesResponse>
        implements ThingModifyCommandResponse<DeleteAttributesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + DeleteAttributes.NAME;

    private static final HttpStatus HTTP_STATUS = HttpStatus.NO_CONTENT;

    private static final CommandResponseJsonDeserializer<DeleteAttributesResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;

    private DeleteAttributesResponse(final ThingId thingId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = ConditionChecker.checkNotNull(thingId, "thingId");
    }

    /**
     * Creates a response to a {@link DeleteAttributes} command.
     *
     * @param thingId the Thing ID of the deleted attributes.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static DeleteAttributesResponse of(final ThingId thingId, final DittoHeaders dittoHeaders) {
        return newInstance(thingId, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code DeleteAttributesResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the attributes were deleted from.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code DeleteAttributesResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code DeleteAttributesResponse}.
     * @since 2.3.0
     */
    public static DeleteAttributesResponse newInstance(final ThingId thingId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new DeleteAttributesResponse(thingId,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        DeleteAttributesResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link DeleteAttributes} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static DeleteAttributesResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link DeleteAttributes} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static DeleteAttributesResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/attributes");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
    }

    @Override
    public DeleteAttributesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, getHttpStatus(), dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DeleteAttributesResponse that = (DeleteAttributesResponse) o;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId) && super.equals(o);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof DeleteAttributesResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + "]";
    }

}
