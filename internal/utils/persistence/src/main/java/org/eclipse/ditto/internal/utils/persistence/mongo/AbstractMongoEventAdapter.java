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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import java.util.Optional;
import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventRegistry;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.EventConfig;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ExtendedActorSystem;
import akka.persistence.journal.EventAdapter;
import akka.persistence.journal.EventSeq;
import akka.persistence.journal.Tagged;

/**
 * Abstract event adapter for persisting Ditto {@link Event}s.
 */
public abstract class AbstractMongoEventAdapter<T extends Event<?>> implements EventAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMongoEventAdapter.class);

    /**
     * Internal header for persisting the historical headers for events.
     */
    public static final JsonFieldDefinition<JsonObject> HISTORICAL_EVENT_HEADERS = JsonFieldDefinition.ofJsonObject(
            "__hh");

    protected final ExtendedActorSystem system;
    protected final EventRegistry<T> eventRegistry;
    private final EventConfig eventConfig;

    protected AbstractMongoEventAdapter(final ExtendedActorSystem system,
            final EventRegistry<T> eventRegistry, final EventConfig eventConfig) {
        this.system = system;
        this.eventRegistry = eventRegistry;
        this.eventConfig = eventConfig;
    }

    @Override
    public String manifest(final Object event) {
        if (event instanceof Event) {
            return ((Event<?>) event).getType();
        } else {
            throw new IllegalArgumentException(
                    "Unable to create manifest for a non-'Event' object! Was: " + event.getClass());
        }
    }

    @Override
    public Object toJournal(final Object event) {
        if (event instanceof Event<?> theEvent) {
            final JsonSchemaVersion schemaVersion = theEvent.getImplementedSchemaVersion();
            final JsonObject jsonObject = performToJournalMigration(theEvent,
                    theEvent.toJson(schemaVersion, FieldType.regularOrSpecial())
            ).build();
            final BsonDocument bson = DittoBsonJson.getInstance().parse(jsonObject);
            final Set<String> tags = theEvent.getDittoHeaders().getJournalTags();
            return new Tagged(bson, tags);
        } else {
            throw new IllegalArgumentException("Unable to toJournal a non-'Event' object! Was: " + event.getClass());
        }
    }

    @Override
    public EventSeq fromJournal(final Object event, final String manifest) {
        if (event instanceof BsonValue bsonValue) {
            final JsonValue jsonValue = DittoBsonJson.getInstance().serialize(bsonValue);
            try {
                final JsonObject jsonObject = jsonValue.asObject()
                        .setValue(EventsourcedEvent.JsonFields.REVISION.getPointer(), Event.DEFAULT_REVISION);
                final DittoHeaders dittoHeaders = jsonObject.getValue(HISTORICAL_EVENT_HEADERS)
                        .map(obj -> DittoHeaders.newBuilder(obj).build())
                        .orElse(DittoHeaders.empty());

                final T result =
                        eventRegistry.parse(performFromJournalMigration(jsonObject), dittoHeaders);
                return EventSeq.single(result);
            } catch (final JsonParseException | DittoRuntimeException e) {
                if (system != null) {
                    system.log().error(e, "Could not deserialize Event JSON: '{}'", jsonValue);
                } else {
                    LOGGER.error("Could not deserialize Event JSON: '{}': {}", jsonValue, e.getMessage());
                }
                return EventSeq.empty();
            }
        } else {
            throw new IllegalArgumentException(
                    "Unable to fromJournal a non-'BsonValue' object! Was: " + event.getClass());
        }
    }

    /**
     * Performs an optional migration of the passed in {@code jsonObject} (which is the JSON representation of the
     * {@link Event} to persist) just before it is transformed to Mongo BSON and inserted into the "journal" collection.
     *
     * @param event the event to apply journal migration for.
     * @param jsonObject the JsonObject representation of the {@link org.eclipse.ditto.base.model.signals.events.Event} to persist.
     * @return the adjusted/migrated JsonObject to store.
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
     * Performs an optional migration of the passed in {@code jsonObject} (which is the JSON representation of the
     * stored BSON in the Mongo collection) just before it is parsed back to an {@link Event} applied to persistence
     * actors for recovery.
     *
     * @param jsonObject the JsonObject as stored in the Mongo collection.
     * @return the adjusted/migrated JsonObject to parse the {@link Event} from.
     */
    protected JsonObject performFromJournalMigration(final JsonObject jsonObject) {
        return jsonObject;
    }

}
