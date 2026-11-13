/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.api.serializer;

import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventRegistry;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.internal.utils.persistence.api.config.EventConfig;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventSeq;

/**
 * Backend-neutral serializer that converts a Ditto {@link Event} to/from its {@link JsonObject} journal
 * representation.
 * <p>
 * This class holds the entire domain&harr;{@code JsonObject} serialization logic for journal events: the manifest
 * computation, the historical-headers enrichment, the journal up-/down-cast migration hooks
 * ({@link #performToJournalMigration(Event, JsonObject)} / {@link #performFromJournalMigration(JsonObject)}) and the
 * parse-and-recover error handling. It contains <em>no</em> backend-specific encoding (no BSON, no JSONB). The
 * MongoDB event adapter wraps this serializer with BSON encoding; a PostgreSQL event adapter feeds it a
 * {@code JsonObject} decoded from JSONB. Both backends reuse the same {@code manifest}/{@code fromJournal} migration
 * path (schema-evolution policy).
 *
 * @param <T> the event type to serialize.
 */
public abstract class EventSerializer<T extends Event<?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventSerializer.class);

    /**
     * Internal header for persisting the historical headers for events.
     */
    public static final JsonFieldDefinition<JsonObject> HISTORICAL_EVENT_HEADERS = JsonFieldDefinition.ofJsonObject(
            "__hh");

    protected final ExtendedActorSystem system;
    protected final EventRegistry<T> eventRegistry;
    private final EventConfig eventConfig;

    protected EventSerializer(final ExtendedActorSystem system,
            final EventRegistry<T> eventRegistry, final EventConfig eventConfig) {
        this.system = system;
        this.eventRegistry = eventRegistry;
        this.eventConfig = eventConfig;
    }

    /**
     * Computes the Pekko manifest for the given object (the event type FQN).
     *
     * @param event the object to compute a manifest for.
     * @return the manifest string.
     */
    public String manifest(final Object event) {
        if (event instanceof Event) {
            return ((Event<?>) event).getType();
        } else {
            throw new IllegalArgumentException(
                    "Unable to create manifest for a non-'Event' object! Was: " + event.getClass());
        }
    }

    /**
     * Serializes the given event to the {@link JsonObject} that should be stored in the journal (after applying the
     * to-journal migration hook). The backend-specific adapter is responsible for wrapping the result in its envelope
     * (BSON / JSONB) and attaching the journal tags from {@link #getJournalTags(Event)}.
     *
     * @param event the event to serialize.
     * @return the migrated JsonObject to store.
     */
    public JsonObject toJournalJson(final Event<?> event) {
        final JsonSchemaVersion schemaVersion = event.getImplementedSchemaVersion();
        return performToJournalMigration(event, event.toJson(schemaVersion, FieldType.regularOrSpecial())).build();
    }

    /**
     * Determines the journal tags to attach to a serialized event. Defaults to the event's
     * {@link DittoHeaders#getJournalTags()}; subclasses may override.
     *
     * @param event the event.
     * @return the set of journal tags.
     */
    public Set<String> getJournalTags(final Event<?> event) {
        return event.getDittoHeaders().getJournalTags();
    }

    /**
     * Deserializes a journal {@link JsonObject} (already decoded from the backend envelope) back to a Ditto event,
     * dispatching first on the stored {@code manifest} (the event type FQN) so subclasses can up-cast or discard
     * removed legacy event types before the payload is parsed. Both backends route through this method, so
     * manifest-keyed migration is shared regardless of the storage envelope.
     *
     * @param jsonObject the JsonObject as decoded from the backend.
     * @param manifest the stored Pekko manifest (event type FQN), or {@code null} if unavailable.
     * @return an {@link EventSeq} with the single recovered event, empty on parse failure or legacy discard.
     */
    public EventSeq fromJournalJson(final JsonObject jsonObject, @Nullable final String manifest) {
        return fromJournalJson(jsonObject);
    }

    /**
     * Deserializes a journal {@link JsonObject} (already decoded from the backend envelope) back to a Ditto event,
     * applying the from-journal migration hook and default-revision/historical-header handling. On a parse failure
     * this logs and returns {@link EventSeq#empty()} mirroring the recovery-tolerant behaviour of the Mongo adapter.
     *
     * @param jsonObject the JsonObject as decoded from the backend.
     * @return an {@link EventSeq} with the single recovered event, or empty on parse failure.
     */
    public EventSeq fromJournalJson(final JsonObject jsonObject) {
        try {
            final JsonObject withRevision = jsonObject
                    .setValue(EventsourcedEvent.JsonFields.REVISION.getPointer(), Event.DEFAULT_REVISION);
            final DittoHeaders dittoHeaders = withRevision.getValue(HISTORICAL_EVENT_HEADERS)
                    // persisted headers were already validated when the event was written -> trust them, skipping
                    // re-parsing/re-validating every JSON-typed header value on each journal read/recovery.
                    .map(DittoHeaders::newFromTrustedJson)
                    .orElse(DittoHeaders.empty());

            final T result = eventRegistry.parse(performFromJournalMigration(withRevision), dittoHeaders);
            return EventSeq.single(result);
        } catch (final JsonParseException | DittoRuntimeException e) {
            if (system != null) {
                system.log().error(e, "Could not deserialize Event JSON: '{}'", jsonObject);
            } else {
                LOGGER.error("Could not deserialize Event JSON: '{}': {}", jsonObject, e.getMessage());
            }
            return EventSeq.empty();
        }
    }

    /**
     * Performs an optional migration of the passed in {@code jsonObject} (the JSON representation of the {@link Event}
     * to persist) just before it is transformed to the backend envelope and inserted into the journal.
     *
     * @param event the event to apply journal migration for.
     * @param jsonObject the JsonObject representation of the {@link Event} to persist.
     * @return the adjusted/migrated JsonObject builder to store.
     */
    protected JsonObjectBuilder performToJournalMigration(final Event<?> event, final JsonObject jsonObject) {
        return jsonObject.toBuilder()
                .set(HISTORICAL_EVENT_HEADERS, calculateHistoricalHeaders(event.getDittoHeaders()).toJson());
    }

    private DittoHeaders calculateHistoricalHeaders(final DittoHeaders dittoHeaders) {
        final DittoHeadersBuilder<?, ?> historicalHeadersBuilder = DittoHeaders.newBuilder();
        eventConfig.getHistoricalHeadersToPersist().forEach(headerKeyToPersist ->
                Optional.ofNullable(dittoHeaders.get(headerKeyToPersist))
                        .ifPresent(value -> historicalHeadersBuilder.putHeader(headerKeyToPersist, value))
        );
        return historicalHeadersBuilder.build();
    }

    /**
     * Performs an optional migration of the passed in {@code jsonObject} (the JSON representation of the stored
     * journal payload) just before it is parsed back to an {@link Event} applied to persistence actors for recovery.
     *
     * @param jsonObject the JsonObject as stored in the journal.
     * @return the adjusted/migrated JsonObject to parse the {@link Event} from.
     */
    protected JsonObject performFromJournalMigration(final JsonObject jsonObject) {
        return jsonObject;
    }

}
