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
package org.eclipse.ditto.protocoladapter;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;

/**
 * Immutable implementation of {@link Payload}.
 */
@Immutable
final class ImmutablePayload implements Payload {

    private final JsonPointer path;
    @Nullable private final JsonValue value;
    @Nullable private final HttpStatusCode status;
    @Nullable private final Long revision;
    @Nullable private final JsonFieldSelector fields;

    private ImmutablePayload(final JsonPointer path,
            @Nullable final JsonValue value,
            @Nullable final HttpStatusCode status,
            @Nullable final Long revision,
            @Nullable final JsonFieldSelector fields) {

        this.path = checkNotNull(path, "path");
        this.value = value;
        this.status = status;
        this.revision = revision;
        this.fields = fields;
    }

    /**
     * Returns a new ImmutablePayload for the specified {@code path}, {@code value}, {@code status}, {@code revision}
     * and {@code fields}.
     *
     * @param path the path.
     * @param value the optional value.
     * @param status the optional status.
     * @param revision the optional revision.
     * @param fields the optional fields.
     * @return the payload.
     * @throws NullPointerException if {@code path} is {@code null}.
     */
    public static ImmutablePayload of(final JsonPointer path,
            @Nullable final JsonValue value,
            @Nullable final HttpStatusCode status,
            @Nullable final Long revision,
            @Nullable final JsonFieldSelector fields) {

        return new ImmutablePayload(path, value, status, revision, fields);
    }

    /**
     * Returns a new ImmutablePayload from the specified {@code jsonObject}.
     *
     * @param jsonObject the JSON object.
     * @return the payload.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} is missing required JSON fields.
     */
    public static ImmutablePayload fromJson(final JsonObject jsonObject) {

        final JsonPointer path = JsonFactory.newPointer(jsonObject.getValueOrThrow(JsonFields.PATH));

        final JsonValue value = jsonObject.getValue(JsonFields.VALUE).orElse(null);

        final HttpStatusCode status = jsonObject.getValue(JsonFields.STATUS)
                .flatMap(HttpStatusCode::forInt)
                .orElse(null);

        final Long revision = jsonObject.getValue(JsonFields.REVISION).orElse(null);

        final JsonFieldSelector fields = jsonObject.getValue(JsonFields.FIELDS)
                .map(JsonFieldSelector::newInstance)
                .orElse(null);

        return of(path, value, status, revision, fields);
    }

    @Override
    public JsonPointer getPath() {
        return path;
    }

    @Override
    public Optional<JsonValue> getValue() {
        return Optional.ofNullable(value);
    }

    @Override
    public Optional<HttpStatusCode> getStatus() {
        return Optional.ofNullable(status);
    }

    @Override
    public Optional<Long> getRevision() {
        return Optional.ofNullable(revision);
    }

    @Override
    public Optional<JsonFieldSelector> getFields() {
        return Optional.ofNullable(fields);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonObject.newBuilder();
        jsonObjectBuilder.set(JsonFields.PATH, path.toString());

        if (null != value) {
            jsonObjectBuilder.set(JsonFields.VALUE, value);
        }

        if (null != status) {
            jsonObjectBuilder.set(JsonFields.STATUS, status.toInt());
        }

        if (null != revision) {
            jsonObjectBuilder.set(JsonFields.REVISION, revision);
        }

        if (null != fields) {
            jsonObjectBuilder.set(JsonFields.FIELDS, fields.toString());
        }

        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutablePayload that = (ImmutablePayload) o;
        return Objects.equals(path, that.path) && Objects.equals(value, that.value) && status == that.status
                && Objects.equals(revision, that.revision) && Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, value, status, revision, fields);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "path=" + path + ", value=" + value + ", status=" + status
                + ", revision=" + revision + ", fields=" + fields + ']';
    }

}
