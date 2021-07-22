/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Immutable implementation of {@link LogEntry}.
 */
@Immutable
final class ImmutableLogEntry implements LogEntry {

    private final String correlationId;
    private final Instant timestamp;
    private final LogCategory logCategory;
    private final LogType logType;
    private final LogLevel logLevel;
    private final String message;
    @Nullable private final String address;
    @Nullable private final EntityId entityId;

    private ImmutableLogEntry(final Builder builder) {
        this.correlationId = builder.correlationId;
        this.timestamp = builder.timestamp;
        this.logCategory = builder.logCategory;
        this.logType = builder.logType;
        this.logLevel = builder.logLevel;
        this.message = builder.message;
        this.address = builder.address;
        this.entityId = builder.entityId;
    }

    public static LogEntryBuilder getBuilder(final String correlationId, final Instant timestamp, final LogCategory logCategory,
            final LogType logType, final LogLevel logLevel, final String message) {
        return getBuilder(correlationId, timestamp, logCategory, logType, logLevel, message, null, null);
    }

    static LogEntryBuilder getBuilder(final String correlationId, final Instant timestamp, final LogCategory logCategory,
            final LogType logType, final LogLevel logLevel, final String message,
            @Nullable final String address, @Nullable final EntityId entityId) {
        return new Builder(correlationId, timestamp, logCategory, logType, logLevel, message)
                .address(address)
                .entityId(entityId);
    }

    public static LogEntry fromJson(final JsonObject jsonObject) {
        final String correlationId = jsonObject.getValueOrThrow(JsonFields.CORRELATION_ID);
        final Instant timestamp = getTimestampOrThrow(jsonObject.getValueOrThrow(JsonFields.TIMESTAMP));
        final LogCategory category = getLogCategoryOrThrow(jsonObject);
        final LogType type = getLogTypeOrThrow(jsonObject);
        final LogLevel level = getLogLevelOrThrow(jsonObject);
        final String message = jsonObject.getValueOrThrow(JsonFields.MESSAGE);
        final String address = jsonObject.getValue(JsonFields.ADDRESS).orElse(null);
        final ThingId thingId = jsonObject.getValue(JsonFields.THING_ID).map(ThingId::of).orElse(null);
        final EntityId entityId;
        if (null != thingId) {
            entityId = thingId;
        } else {
            entityId = jsonObject.getValue(JsonFields.ENTITY_ID)
                    .map(eId -> EntityId.of(EntityType.of("unknown"), eId))
                    .orElse(null);
        }

        return getBuilder(correlationId, timestamp, category, type, level, message, address, entityId)
                .build();
    }

    private static Instant getTimestampOrThrow(final CharSequence dateTime) {
        try {
            return Instant.parse(dateTime);
        } catch (final DateTimeParseException e) {
            final String msgPattern = "The JSON object''s field <{0}> is not in ISO-8601 format as expected!";
            throw JsonParseException.newBuilder()
                    .message(MessageFormat.format(msgPattern, JsonFields.TIMESTAMP.getPointer()))
                    .cause(e)
                    .build();
        }
    }

    private static LogCategory getLogCategoryOrThrow(final JsonObject jsonObject) {
        final String readLogCategory = jsonObject.getValueOrThrow(JsonFields.CATEGORY);
        return LogCategory.forName(readLogCategory)
                .orElseThrow(() -> JsonParseException.newBuilder()
                        .message(MessageFormat.format("Log category <{0}> is invalid!", readLogCategory))
                        .build());
    }

    private static LogType getLogTypeOrThrow(final JsonObject jsonObject) {
        final String readLogType = jsonObject.getValueOrThrow(JsonFields.TYPE);
        return LogType.forType(readLogType)
                .orElseThrow(() -> JsonParseException.newBuilder()
                        .message(MessageFormat.format("Log type <{0}> is invalid!", readLogType))
                        .build());
    }

    private static LogLevel getLogLevelOrThrow(final JsonObject jsonObject) {
        final String readLogLevel = jsonObject.getValueOrThrow(JsonFields.LEVEL);
        return LogLevel.forLevel(readLogLevel)
                .orElseThrow(() -> JsonParseException.newBuilder()
                        .message(MessageFormat.format("Log level <{0}> is invalid!", readLogLevel))
                        .build());
    }

    @Override
    public String getCorrelationId() {
        return correlationId;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public LogCategory getLogCategory() {
        return logCategory;
    }

    @Override
    public LogType getLogType() {
        return logType;
    }

    @Override
    public LogLevel getLogLevel() {
        return logLevel;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public Optional<String> getAddress() {
        return Optional.ofNullable(address);
    }

    @Override
    public Optional<EntityId> getEntityId() {
        return Optional.ofNullable(entityId);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder()
                .set(JsonFields.CORRELATION_ID, correlationId)
                .set(JsonFields.TIMESTAMP, timestamp.toString())
                .set(JsonFields.CATEGORY, logCategory.getName())
                .set(JsonFields.TYPE, logType.getType())
                .set(JsonFields.LEVEL, logLevel.getLevel())
                .set(JsonFields.MESSAGE, message);
        if (null != address) {
            builder.set(JsonFields.ADDRESS, address);
        }
        if (null != entityId) {
            if (entityId instanceof ThingId) {
                builder.set(JsonFields.THING_ID, entityId.toString());
            }
            builder.set(JsonFields.ENTITY_ID, entityId.toString());
        }
        return builder.build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableLogEntry that = (ImmutableLogEntry) o;
        return Objects.equals(correlationId, that.correlationId) &&
                Objects.equals(timestamp, that.timestamp) &&
                logCategory == that.logCategory &&
                logType == that.logType &&
                logLevel == that.logLevel &&
                Objects.equals(message, that.message) &&
                Objects.equals(address, that.address) &&
                Objects.equals(entityId, that.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(correlationId, timestamp, logCategory, logType, logLevel, message, address, entityId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "correlationId=" + correlationId +
                ", timestamp=" + timestamp +
                ", logCategory=" + logCategory +
                ", logType=" + logType +
                ", logLevel=" + logLevel +
                ", message=" + message +
                ", address=" + address +
                ", entityId=" + entityId +
                "]";
    }

    /**
     * Builder for {@code ImmutableLogEntry}.
     */
    @NotThreadSafe
    private static final class Builder implements LogEntryBuilder {

        private String correlationId;
        private Instant timestamp;
        private LogCategory logCategory;
        private LogType logType;
        private LogLevel logLevel;
        private String message;

        @Nullable private String address;
        @Nullable private EntityId entityId;

        Builder(final String correlationId, final Instant timestamp, final LogCategory logCategory,
                final LogType logType, final LogLevel logLevel, final String message) {
            this.correlationId = checkNotNull(correlationId, "correlation id");
            this.timestamp = checkNotNull(timestamp, "timestamp");
            this.logCategory = checkNotNull(logCategory, "log category");
            this.logType = checkNotNull(logType, "log type");
            this.logLevel = checkNotNull(logLevel, "log level");
            this.message = checkNotNull(message, "message");
        }

        @Override
        public LogEntryBuilder correlationId(final String correlationId) {
            this.correlationId = checkNotNull(correlationId);
            return this;
        }

        @Override
        public LogEntryBuilder timestamp(final Instant timestamp) {
            this.timestamp = checkNotNull(timestamp);
            return this;
        }

        @Override
        public LogEntryBuilder logCategory(final LogCategory logCategory) {
            this.logCategory = checkNotNull(logCategory);
            return this;
        }

        @Override
        public LogEntryBuilder logType(final LogType logType) {
            this.logType = checkNotNull(logType);
            return this;
        }

        @Override
        public LogEntryBuilder logLevel(final LogLevel logLevel) {
            this.logLevel = checkNotNull(logLevel);
            return this;
        }

        @Override
        public LogEntryBuilder message(final String message) {
            this.message = checkNotNull(message);
            return this;
        }

        @Override
        public LogEntryBuilder address(@Nullable final String address) {
            this.address = address;
            return this;
        }

        @Override
        public LogEntryBuilder entityId(@Nullable final EntityId entityId) {
            this.entityId = entityId;
            return this;
        }

        @Override
        public LogEntry build() {
            return new ImmutableLogEntry(this);
        }

    }

}
