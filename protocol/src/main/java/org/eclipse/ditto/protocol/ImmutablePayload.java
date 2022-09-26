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
package org.eclipse.ditto.protocol;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable implementation of {@link Payload}.
 */
@Immutable
final class ImmutablePayload implements Payload {

    private final MessagePath path;
    @Nullable private final JsonValue value;
    @Nullable private final JsonObject extra;
    @Nullable private final HttpStatus status;
    @Nullable private final Long revision;
    @Nullable private final Instant timestamp;
    @Nullable private final Metadata metadata;
    @Nullable private final JsonFieldSelector fields;

    private ImmutablePayload(final ImmutablePayloadBuilder builder) {
        path = builder.path;
        value = builder.value;
        extra = builder.extra;
        status = builder.status;
        revision = builder.revision;
        timestamp = builder.timestamp;
        metadata = builder.metadata;
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

        final ImmutablePayloadBuilder payloadBuilder = getBuilder(ImmutableTopicPath.newTopicOrPathPointer(readPath))
                .withValue(jsonObject.getValue(JsonFields.VALUE).orElse(null))
                .withExtra(jsonObject.getValue(JsonFields.EXTRA).orElse(null))
                .withStatus(jsonObject.getValue(JsonFields.STATUS).flatMap(HttpStatus::tryGetInstance).orElse(null))
                .withTimestamp(jsonObject.getValue(JsonFields.TIMESTAMP).map(Instant::parse).orElse(null))
                .withMetadata(jsonObject.getValue(JsonFields.METADATA).map(Metadata::newMetadata).orElse(null))
                .withFields(jsonObject.getValue(JsonFields.FIELDS)
                        .map(JsonFactory::parseJsonFieldSelector)
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
    public Optional<HttpStatus> getHttpStatus() {
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
    public Optional<Metadata> getMetadata() {
        return Optional.ofNullable(metadata);
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
            jsonObjectBuilder.set(JsonFields.STATUS, status.getCode());
        }
        if (null != revision) {
            jsonObjectBuilder.set(JsonFields.REVISION, revision);
        }
        if (null != timestamp) {
            jsonObjectBuilder.set(JsonFields.TIMESTAMP, timestamp.toString());
        }
        if (null != metadata) {
            jsonObjectBuilder.set(JsonFields.METADATA, metadata.toJson());
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
                && Objects.equals(status, that.status)
                && Objects.equals(revision, that.revision)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(metadata, that.metadata)
                && Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, value, extra, status, revision, timestamp, metadata, fields);
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
                ", metadata=" + metadata +
                ", fields=" + fields +
                "]";
    }

    /**
     * Mutable implementation of {@link PayloadBuilder} for building immutable {@link Payload} instances.
     */
    @NotThreadSafe
    static final class ImmutablePayloadBuilder implements PayloadBuilder {

        @Nullable private MessagePath path;

        @Nullable private JsonValue value;
        @Nullable private JsonObject extra;
        @Nullable private HttpStatus status;
        @Nullable private Long revision;
        @Nullable private Instant timestamp;
        @Nullable private Metadata metadata;
        @Nullable private JsonFieldSelector fields;

        ImmutablePayloadBuilder(final Payload payload) {
            path = payload.getPath();
            value = payload.getValue().orElse(null);
            extra = payload.getExtra().orElse(null);
            status = payload.getHttpStatus().orElse(null);
            revision = payload.getRevision().orElse(null);
            timestamp = payload.getTimestamp().orElse(null);
            metadata = payload.getMetadata().orElse(null);
            fields = payload.getFields().orElse(null);
        }

        private ImmutablePayloadBuilder(final JsonPointer path) {
            this.path = toMessagePath(path);
            value = null;
            extra = null;
            status = null;
            timestamp = null;
            metadata = null;
            fields = null;
        }

        @Override
        public PayloadBuilder withPath(@Nullable final JsonPointer path) {
            this.path = toMessagePath(path);
            return this;
        }

        @Override
        public ImmutablePayloadBuilder withValue(@Nullable final JsonValue value) {
            this.value = value;
            return this;
        }

        @Override
        public ImmutablePayloadBuilder withExtra(@Nullable final JsonObject extra) {
            this.extra = extra;
            return this;
        }

        @Override
        public ImmutablePayloadBuilder withStatus(@Nullable final HttpStatus status) {
            this.status = status;
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
        public ImmutablePayloadBuilder withMetadata(@Nullable final Metadata metadata) {
            this.metadata = metadata;
            return this;
        }

        @Override
        public ImmutablePayloadBuilder withFields(@Nullable final JsonFieldSelector fields) {
            this.fields = fields;
            return this;
        }

        @Override
        public ImmutablePayloadBuilder withFields(@Nullable final String fields) {
            this.fields = null != fields ? JsonFieldSelector.newInstance(fields) : null;
            return this;
        }

        @Override
        public ImmutablePayload build() {
            return new ImmutablePayload(this);
        }

        private static MessagePath toMessagePath(final JsonPointer path) {
            checkNotNull(path, "path");
            if (path instanceof MessagePath) {
                return (MessagePath) path;
            } else {
                return ImmutableMessagePath.of(path);
            }
        }

    }

}
