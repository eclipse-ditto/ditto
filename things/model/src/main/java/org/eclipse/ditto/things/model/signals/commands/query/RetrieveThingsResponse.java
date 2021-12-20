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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.base.model.signals.commands.WithEntity;
import org.eclipse.ditto.base.model.signals.commands.WithNamespace;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link RetrieveThings} command.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveThingsResponse.TYPE)
public final class RetrieveThingsResponse extends AbstractCommandResponse<RetrieveThingsResponse>
        implements WithNamespace, WithEntity<RetrieveThingsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = ThingCommandResponse.TYPE_PREFIX + RetrieveThings.NAME;

    static final JsonFieldDefinition<JsonArray> JSON_THINGS =
            JsonFieldDefinition.ofJsonArray("things", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_THINGS_PLAIN_JSON =
            JsonFieldDefinition.ofString("thingsPlainJson", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_NAMESPACE =
            JsonFieldDefinition.ofString("namespace", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final String PROPERTY_NAME_THINGS = "Things";

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveThingsResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();

                        final JsonArray thingsJsonArray = jsonObject.getValue(JSON_THINGS).orElse(null);

                        return newInstance(thingsJsonArray,
                                jsonObject.getValue(JSON_THINGS_PLAIN_JSON)
                                        .orElseGet(() -> String.valueOf(thingsJsonArray)),
                                jsonObject.getValue(JSON_NAMESPACE).orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders());
                    });

    private final String thingsPlainJson;
    @Nullable private final String namespace;
    @Nullable private JsonArray things;

    private RetrieveThingsResponse(@Nullable final JsonArray things,
            final String thingsPlainJson,
            @Nullable final String namespace,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingsPlainJson = checkNotNull(thingsPlainJson, "thingsPlainJson");
        this.namespace = namespace;
        this.things = things;
    }

    /**
     * Creates a response to a {@link RetrieveThings} command.
     *
     * @param thingsPlainJson the retrieved Things.
     * @param namespace the namespace of this search request
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveThingsResponse of(final List<String> thingsPlainJson,
            @Nullable final String namespace,
            final DittoHeaders dittoHeaders) {

        return newInstance(null,
                thingsPlainJson.stream().collect(Collectors.joining(",", "[", "]")),
                namespace,
                HTTP_STATUS,
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThings} command.
     *
     * @param thingsPlainJson the retrieved Things.
     * @param namespace the namespace of this search request
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code thingsPlainJson} or {@code dittoHeaders} is {@code null}.
     */
    public static RetrieveThingsResponse of(final String thingsPlainJson,
            @Nullable final String namespace,
            final DittoHeaders dittoHeaders) {

        return newInstance(null, thingsPlainJson, namespace, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThings} command.
     *
     * @param things the retrieved Things.
     * @param namespace the namespace of this search request.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code things} or {@code dittoHeaders} is {@code null}.
     */
    public static RetrieveThingsResponse of(final JsonArray things,
            @Nullable final String namespace,
            final DittoHeaders dittoHeaders) {

        return newInstance(checkNotNull(things, "things"), things.toString(), namespace, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThings} command.
     *
     * @param things the retrieved Things.
     * @param predicate the predicate to apply to the things when transforming to JSON.
     * @param namespace the namespace of this search request.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument but {@code namespace} is {@code null}.
     */
    public static RetrieveThingsResponse of(final List<Thing> things,
            final Predicate<JsonField> predicate,
            @Nullable final String namespace,
            final DittoHeaders dittoHeaders) {

        final JsonArray thingsArray = checkNotNull(things, PROPERTY_NAME_THINGS).stream()
                .map(thing -> thing.toJson(dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST), predicate))
                .collect(JsonCollectors.valuesToArray());
        return newInstance(thingsArray, thingsArray.toString(), namespace, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThings} command.
     *
     * @param things the retrieved Things.
     * @param fieldSelector the JsonFieldSelector to apply to the passed things when transforming to JSON.
     * @param predicate the predicate to apply to the things when transforming to JSON.
     * @param namespace the namespace of this retrieve things request
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code things} or {@code dittoHeaders} is {@code null}.
     */
    public static RetrieveThingsResponse of(final List<Thing> things,
            @Nullable final JsonFieldSelector fieldSelector,
            @Nullable final Predicate<JsonField> predicate,
            @Nullable final String namespace,
            final DittoHeaders dittoHeaders) {

        final JsonArray thingsArray = checkNotNull(things, PROPERTY_NAME_THINGS).stream()
                .map(thing -> getJsonFields(fieldSelector, predicate, dittoHeaders, thing))
                .collect(JsonCollectors.valuesToArray());
        return newInstance(thingsArray, thingsArray.toString(), namespace, HTTP_STATUS, dittoHeaders);
    }

    private static JsonObject getJsonFields(@Nullable final JsonFieldSelector fieldSelector,
            @Nullable final Predicate<JsonField> predicate,
            final DittoHeaders dittoHeaders,
            final Thing thing) {

        if (fieldSelector != null) {
            return predicate != null ?
                    thing.toJson(dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST),
                            fieldSelector,
                            predicate) :
                    thing.toJson(dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST), fieldSelector);
        } else {
            return predicate != null ?
                    thing.toJson(dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST), predicate) :
                    thing.toJson(dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST));
        }
    }

    /**
     * Returns a new instance of {@code RetrieveThingsResponse} for the specified arguments.
     *
     * @param things the retrieved Things or {@code null} if only {@code thingsPlainJson} is known.
     * @param thingsPlainJson the retrieved Things as JSON array string.
     * @param namespace the namespace of the retrieved things.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code RetrieveThingsResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code RetrieveThingsResponse}.
     * @since 2.3.0
     */
    public static RetrieveThingsResponse newInstance(@Nullable final JsonArray things,
            final String thingsPlainJson,
            @Nullable final String namespace,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new RetrieveThingsResponse(things,
                thingsPlainJson,
                namespace,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveThingsResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThings} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveThingsResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject = DittoJsonException.wrapJsonRuntimeException(() -> JsonObject.of(jsonString));
        return fromJson(jsonObject, dittoHeaders);
    }

    /**
     * Creates a response to a {@link RetrieveThings} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveThingsResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.ofNullable(namespace);
    }

    /**
     * Returns the retrieved Things.
     *
     * @return the retrieved Things.
     */
    public List<Thing> getThings() {
        return getThingStream(lazyLoadThingsJsonArray()).collect(Collectors.toList());
    }

    private static Stream<Thing> getThingStream(final JsonArray thingsArray) {
        return thingsArray.stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(ThingsModelFactory::newThing);
    }

    private JsonArray lazyLoadThingsJsonArray() {
        if (things == null) {
            things = JsonArray.of(thingsPlainJson);
        }
        return things;
    }

    @Override
    public Optional<String> getEntityPlainString() {
        return Optional.of(thingsPlainJson);
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return lazyLoadThingsJsonArray();
    }

    @Override
    public RetrieveThingsResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        if (!entity.isArray()) {
            throw new IllegalArgumentException(MessageFormat.format("Entity is not a JSON array but <{0}>.", entity));
        }
        return of(entity.asArray(), namespace, getDittoHeaders());
    }

    @Override
    public RetrieveThingsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingsPlainJson, namespace, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty(); // no path for retrieve of multiple things
    }

    @Override
    public String getResourceType() {
        return null;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_THINGS_PLAIN_JSON, thingsPlainJson, predicate);
        if (namespace != null) {
            jsonObjectBuilder.set(JSON_NAMESPACE, namespace, predicate);
        }
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveThingsResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveThingsResponse that = (RetrieveThingsResponse) o;
        return that.canEqual(this) &&
                Objects.equals(things, that.things) &&
                Objects.equals(thingsPlainJson, that.thingsPlainJson) &&
                Objects.equals(namespace, that.namespace) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), things, thingsPlainJson, namespace);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", things=" + things +
                ", thingsPlainJson=" + thingsPlainJson + ", namespace=" + namespace + "]";
    }

}
