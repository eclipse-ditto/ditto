/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.api.persistence;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonPointerInvalidException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;

/**
 * An event to be emitted when a snapshot of an entity was taken.
 *
 * @param <T> the type of the extending event.
 */
@Immutable
public abstract class SnapshotTaken<T extends SnapshotTaken<T>>
        implements Event<T>, EventsourcedEvent<T>, WithEntityId {

    private final String type;
    private final long revision;
    @Nullable
    private final Instant timestamp;
    @Nullable
    private final Metadata metadata;
    private final JsonObject entityOfSnapshot;
    private final PersistenceLifecycle lifecycle;
    private final DittoHeaders dittoHeaders;

    /**
     * Constructs a new {@code SnapshotTaken}.
     *
     * @param type the type of the event.
     * @param revision the revision of the entity of which a snapshot was taken.
     * @param timestamp the timestamp when the snapshot was taken.
     * @param metadata the event's metadata.
     * @param entityOfSnapshot the JSON representation of the entity of which a snapshot was taken.
     * @param lifecycle the lifecycle state of an entity.
     * @param dittoHeaders the event's DittoHeaders.
     * @throws NullPointerException if any argument but {@code timestamp} or {@code metadata} is {@code null}.
     */
    protected SnapshotTaken(final String type,
            final long revision,
            @Nullable final Instant timestamp,
            @Nullable final Metadata metadata,
            final JsonObject entityOfSnapshot,
            final PersistenceLifecycle lifecycle,
            final DittoHeaders dittoHeaders) {

        this.type = checkNotNull(type, "type");
        this.revision = revision;
        this.timestamp = timestamp;
        this.metadata = metadata;
        this.entityOfSnapshot = checkNotNull(entityOfSnapshot, "entityOfSnapshot");
        this.lifecycle = checkNotNull(lifecycle, "lifecycle");
        this.dittoHeaders = checkNotNull(dittoHeaders, "dittoHeaders");
    }

    protected static PersistenceLifecycle getPersistenceLifecycle(final String lifecycleName) {
        return PersistenceLifecycle.forName(lifecycleName)
                .orElseThrow(() -> {
                    final var pattern = "Failed to map <{0}> to a persistence lifecycle.";
                    return new IllegalArgumentException(MessageFormat.format(pattern, lifecycleName));
                });
    }

    /**
     * Gets the ID of the entity of which a snapshot was taken.
     *
     * @return the ID.
     */
    @Override
    public abstract NamespacedEntityId getEntityId();

    /**
     * Gets the lifecycle state of the persisted entity.
     *
     * @return the lifecycle.
     */
    public PersistenceLifecycle getLifecycle() {
        return lifecycle;
    }

    /**
     * Returns the topic for subscribing via pub-sub for this event.
     */
    public abstract String getPubSubTopic();

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(entityOfSnapshot);
    }

    /**
     * Gets the revision of the entity of which a snapshot was taken.
     *
     * @return the revision.
     */
    @Override
    public long getRevision() {
        return revision;
    }

    /**
     * Gets the type of the event.
     *
     * @return the type.
     */
    @Override
    public String getType() {
        return type;
    }

    /**
     * @return see {@link #getType()}
     */
    @Override
    public String getManifest() {
        return getType();
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
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Override
    public T setDittoHeaders(final DittoHeaders dittoHeaders) {
        return setDittoHeaders(checkNotNull(dittoHeaders, "dittoHeaders"), entityOfSnapshot);
    }

    /**
     * Helps subclasses to implement {@link #setDittoHeaders(DittoHeaders)} as it provides the entity JSON for
     * constructing a new SnapshotTaken event.
     *
     * @param dittoHeaders the new DittoHeaders to be set.
     * @param entityOfSnapshot the JSON representation of the entity of which a snapshot was taken.
     * @return the new SnapshotTaken event with {@code dittoHeaders} set.
     */
    protected abstract T setDittoHeaders(DittoHeaders dittoHeaders, JsonObject entityOfSnapshot);

    /**
     * @return always an empty JSON pointer
     */
    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final var predicate = schemaVersion.and(thePredicate);

        return JsonFactory.newObjectBuilder()
                .set(Event.JsonFields.TYPE, type) // always include the type ignoring the predicate
                .set(EventsourcedEvent.JsonFields.REVISION, revision, predicate)
                .set(Event.JsonFields.TIMESTAMP,
                        getTimestamp().map(Instant::toString).orElse(null),
                        predicate.and(JsonField.isValueNonNull()))
                .set(Event.JsonFields.METADATA,
                        getMetadata().map(m -> m.toJson(schemaVersion, predicate)).orElse(null),
                        predicate.and(JsonField.isValueNonNull()))
                .set(JsonFields.ENTITY_ID, String.valueOf(getEntityId()), predicate)
                .set(JsonFields.ENTITY, entityOfSnapshot)
                .set(JsonFields.LIFECYCLE, lifecycle.name(), predicate)
                .build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (SnapshotTaken<?>) o;
        return Objects.equals(type, that.type) &&
                revision == that.revision &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(metadata, that.metadata) &&
                Objects.equals(entityOfSnapshot, that.entityOfSnapshot) &&
                lifecycle == that.lifecycle &&
                Objects.equals(dittoHeaders, that.dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, revision, timestamp, metadata, entityOfSnapshot, lifecycle, dittoHeaders);
    }

    @Override
    public String toString() {
        return "type=" + type +
                ", revision=" + revision +
                ", timestamp=" + timestamp +
                ", metadata=" + metadata +
                ", entityOfSnapshot=" + entityOfSnapshot +
                ", lifecycle=" + lifecycle +
                ", dittoHeaders=" + dittoHeaders;
    }

    /**
     * An enumeration of definitions of the known JSON fields of a {@link SnapshotTaken}.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * Definition of a JSON field containing the entity ID.
         * Key of the field is {@code "entityId"}.
         */
        public static final JsonFieldDefinition<String> ENTITY_ID =
                JsonFieldDefinition.ofString("entityId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * Definition of a JSON field containing the lifecycle of the entity of which a snapshot was taken.
         * Key of the field is {@code "lifecycle"}.
         */
        public static final JsonFieldDefinition<String> LIFECYCLE =
                JsonFieldDefinition.ofString("lifecycle", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * Definition of a JSON field containing the entity.
         * Key of the field is {@code "entity"}.
         */
        public static final JsonFieldDefinition<JsonObject> ENTITY =
                JsonFieldDefinition.ofJsonObject("entity", FieldType.REGULAR, JsonSchemaVersion.V_2);


        private JsonFields() {
            throw new AssertionError();
        }

    }

    /**
     * This class provides methods for safely deserializing a {@link JsonObject} to an instance of
     * {@code SnapshotTaken}.
     * "Safe" means that known potential exceptions get caught and wrapped into a {@link JsonParseException} as cause
     * together with an informative message to help to identify the root of the problem.
     */
    @Immutable
    protected static final class JsonDeserializer {

        private final JsonObject jsonObject;

        private JsonDeserializer(final JsonObject jsonObject) {
            this.jsonObject = jsonObject;
        }

        /**
         * Returns an instance of {@code JsonDeserializer} for the specified JSON object and the expected event type.
         *
         * @param jsonObject the JSON object to be deserialized.
         * @param eventType the expected type of the SnapshotTaken event to be deserialized.
         * @return the deserializer.
         * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain the mandatory
         * {@link Event.JsonFields#TYPE}.
         * @throws JsonParseException if {@code jsonObject} contained a different value than {@code type} for
         * {@link Event.JsonFields#TYPE}.
         * @throws NullPointerException if any argument is {@code null}.
         */
        public static JsonDeserializer of(final JsonObject jsonObject, final String eventType) {
            checkNotNull(eventType, "eventType");
            checkNotNull(jsonObject, "jsonObject");
            validateType(eventType, deserializeType(jsonObject));
            return new JsonDeserializer(jsonObject);
        }

        private static String deserializeType(final JsonObject jsonObject) {
            return jsonObject.getValueOrThrow(Event.JsonFields.TYPE);
        }

        private static void validateType(final String expectedType, final String actualType) {
            if (!Objects.equals(expectedType, actualType)) {
                final var pattern = "Failed to deserialize {0}: actual type <{1}> does not match expected type <{2}>";
                throw JsonParseException.newBuilder()
                        .message(MessageFormat.format(pattern, SnapshotTaken.class.getName(), actualType, expectedType))
                        .build();
            }
        }

        /**
         * Returns the value for {@link EventsourcedEvent.JsonFields#REVISION} from the wrapped JSON object.
         *
         * @return the revision number.
         * @throws org.eclipse.ditto.json.JsonMissingFieldException if the wrapped JSON object did not contain this
         * field at all.
         * @throws JsonParseException if the value of the JSON field was no {@code long}.
         */
        public long deserializeRevision() {
            return jsonObject.getValueOrThrow(EventsourcedEvent.JsonFields.REVISION);
        }

        /**
         * Returns the value for {@link Event.JsonFields#TIMESTAMP} from the wrapped JSON object.
         *
         * @return the timestamp or {@code null}.
         * @throws JsonParseException if the value of the JSON field was no Instant.
         */
        @Nullable
        public Instant deserializeTimestamp() {
            final var fieldDefinition = Event.JsonFields.TIMESTAMP;
            return jsonObject.getValue(fieldDefinition)
                    .map(timestampIsoString -> {
                        try {
                            return Instant.parse(timestampIsoString);
                        } catch (final DateTimeParseException e) {
                            throw getJsonParseException(fieldDefinition, Instant.class, e);
                        }
                    })
                    .orElse(null);
        }

        /**
         * Returns the value for {@link Event.JsonFields#METADATA} from the wrapped JSON object.
         *
         * @return the metadata or {@code null}.
         * @throws JsonParseException if the value of the JSON field was no {@code Metadata}.
         */
        @Nullable
        public Metadata deserializeMetadata() {
            final var fieldDefinition = Event.JsonFields.METADATA;
            return jsonObject.getValue(fieldDefinition)
                    .map(metadataJsonObject -> {
                        try {
                            return Metadata.newMetadata(metadataJsonObject);
                        } catch (final JsonPointerInvalidException e) {
                            throw getJsonParseException(fieldDefinition, Metadata.class, e);
                        }
                    })
                    .orElse(null);
        }

        /**
         * Returns the value for {@link SnapshotTaken.JsonFields#ENTITY} from the wrapped JSON object.
         *
         * @return the entity.
         * @throws org.eclipse.ditto.json.JsonMissingFieldException if the wrapped JSON object did not contain this
         * field at all.
         * @throws JsonParseException if the value of the JSON field was no {@code JsonObject}.
         */
        public JsonObject deserializeEntity() {
            return jsonObject.getValueOrThrow(JsonFields.ENTITY);
        }

        /**
         * Returns the value for {@link SnapshotTaken.JsonFields#LIFECYCLE} from the wrapped JSON object.
         *
         * @return the lifecycle.
         * @throws org.eclipse.ditto.json.JsonMissingFieldException if the wrapped JSON object did not contain this
         * field at all.
         * @throws JsonParseException if the value of the JSON field was no {@code PersistenceLifecycle}.
         */
        public PersistenceLifecycle deserializePersistenceLifecycle() {
            final var fieldDefinition = JsonFields.LIFECYCLE;
            final var lifecycleName = jsonObject.getValueOrThrow(fieldDefinition);
            try {
                return getPersistenceLifecycle(lifecycleName);
            } catch (final IllegalArgumentException e) {
                throw getJsonParseException(fieldDefinition, PersistenceLifecycle.class, e);
            }
        }

        /**
         * Creates a JsonParseException with an informative message to make it easier to find the root of the
         * deserialization problem.
         *
         * @param jsonFieldDefinition provides information about the field whose value could not be deserialized.
         * @param targetType the type which should have been deserialized from the value of {@code jsonFieldDefinition}.
         * @param cause the cause of the deserialization problem.
         * @return the JsonParseException.
         * @throws NullPointerException if any argument is {@code null}.
         */
        public static JsonParseException getJsonParseException(final JsonFieldDefinition<?> jsonFieldDefinition,
                final Class<?> targetType,
                final Throwable cause) {
            checkNotNull(jsonFieldDefinition, "jsonFieldDefinition");
            checkNotNull(targetType, "targetType");
            checkNotNull(cause, "cause");

            return JsonParseException.newBuilder()
                    .message(MessageFormat.format("Failed to deserialize field <{0}> as {1}: {2}",
                            jsonFieldDefinition.getPointer(),
                            targetType.getName(),
                            cause.getMessage()))
                    .cause(cause)
                    .build();
        }

    }

}
