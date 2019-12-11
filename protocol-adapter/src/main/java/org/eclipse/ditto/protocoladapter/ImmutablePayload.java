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
package org.eclipse.ditto.protocoladapter;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

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

    private final MessagePath path;
    @Nullable private final JsonValue value;
    @Nullable private final JsonObject extra;
    @Nullable private final HttpStatusCode status;
    @Nullable private final Long revision;
    @Nullable private final Instant timestamp;
    @Nullable private final JsonFieldSelector fields;

    private ImmutablePayload(final ImmutablePayloadBuilder builder) {
        path = builder.path;
        value = builder.value;
        extra = builder.extra;
        status = builder.status;
        revision = builder.revision;
        timestamp = builder.timestamp;
        fields = builder.fields;
    }

    /**
     * Returns a mutable builder with a fluent API for creating an ImmutablePayload.
     *
     * @param path the path of the payload to be built.
     * @return the builder.
     */
    public static ImmutablePayloadBuilder getBuilder(@Nullable final JsonPointer path) {
        return new ImmutablePayloadBuilder(path);
    }

    /**
     * Returns a new ImmutablePayload from the specified {@code jsonObject}.
     *
     * @param jsonObject the JSON object.
     * @return the payload.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} is missing required JSON fields.
     */
    public static ImmutablePayload fromJson(final JsonObject jsonObject) {
        final String readPath = jsonObject.getValueOrThrow(JsonFields.PATH);

        final ImmutablePayloadBuilder payloadBuilder = getBuilder(JsonFactory.newPointer(readPath))
                        .withValue(jsonObject.getValue(JsonFields.VALUE).orElse(null))
                        .withExtra(jsonObject.getValue(JsonFields.EXTRA).orElse(null))
                        .withStatus(jsonObject.getValue(JsonFields.STATUS).flatMap(HttpStatusCode::forInt).orElse(null))
                        .withTimestamp(jsonObject.getValue(JsonFields.TIMESTAMP).map(Instant::parse).orElse(null))
                        .withFields(jsonObject.getValue(JsonFields.FIELDS)
                                .map(JsonFieldSelector::newInstance)
                                .orElse(null));

        jsonObject.getValue(JsonFields.REVISION).ifPresent(payloadBuilder::withRevision);

        return payloadBuilder.build();
    }

    @Override
    public MessagePath getPath() {
        return path;
    }

    @Override
    public Optional<JsonValue> getValue() {
        return Optional.ofNullable(value);
    }

    @Override
    public Optional<JsonObject> getExtra() {
        return Optional.ofNullable(extra);
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
    public Optional<Instant> getTimestamp() {
        return Optional.ofNullable(timestamp);
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
        if (null != extra) {
            jsonObjectBuilder.set(JsonFields.EXTRA, extra);
        }
        if (null != status) {
            jsonObjectBuilder.set(JsonFields.STATUS, status.toInt());
        }
        if (null != revision) {
            jsonObjectBuilder.set(JsonFields.REVISION, revision);
        }
        if (null != timestamp) {
            jsonObjectBuilder.set(JsonFields.TIMESTAMP, timestamp.toString());
        }
        if (null != fields) {
            jsonObjectBuilder.set(JsonFields.FIELDS, fields.toString());
        }

        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutablePayload that = (ImmutablePayload) o;
        return Objects.equals(path, that.path)
                && Objects.equals(value, that.value)
                && Objects.equals(extra, that.extra)
                && status == that.status
                && Objects.equals(revision, that.revision)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, value, extra, status, revision, timestamp, fields);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "path=" + path +
                ", value=" + value +
                ", extra=" + extra +
                ", status=" + status +
                ", revision=" + revision +
                ", timestamp=" + timestamp +
                ", fields=" + fields +
                "]";
    }

    /**
     * Mutable implementation of {@link PayloadBuilder} for building immutable {@link Payload} instances.
     */
    @NotThreadSafe
    static final class ImmutablePayloadBuilder implements PayloadBuilder {

        @Nullable private final MessagePath path;

        @Nullable private JsonValue value;
        @Nullable private JsonObject extra;
        @Nullable private HttpStatusCode status;
        @Nullable private Long revision;
        @Nullable private Instant timestamp;
        @Nullable private JsonFieldSelector fields;

        private ImmutablePayloadBuilder(@Nullable final JsonPointer path) {
            this.path = null != path ? ImmutableMessagePath.of(path) : null;
            value = null;
            extra = null;
            status = null;
            timestamp = null;
            fields = null;
        }

        @Override
        public ImmutablePayloadBuilder withValue(final JsonValue value) {
            this.value = value;
            return this;
        }

        @Override
        public ImmutablePayloadBuilder withExtra(@Nullable final JsonObject extra) {
            this.extra = extra;
            return this;
        }

        @Override
        public ImmutablePayloadBuilder withStatus(final HttpStatusCode status) {
            this.status = status;
            return this;
        }

        @Override
        public ImmutablePayloadBuilder withStatus(final int status) {
            this.status = HttpStatusCode.forInt(status) //
                    .orElseThrow(() -> new IllegalArgumentException("Status code not supported!"));
            return this;
        }

        @Override
        public ImmutablePayloadBuilder withRevision(final long revision) {
            this.revision = revision;
            return this;
        }

        @Override
        public ImmutablePayloadBuilder withTimestamp(@Nullable final Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        @Override
        public ImmutablePayloadBuilder withFields(final JsonFieldSelector fields) {
            this.fields = fields;
            return this;
        }

        @Override
        public ImmutablePayloadBuilder withFields(final String fields) {
            this.fields = JsonFieldSelector.newInstance(fields);
            return this;
        }

        @Override
        public ImmutablePayload build() {
            return new ImmutablePayload(this);
        }

    }

}
