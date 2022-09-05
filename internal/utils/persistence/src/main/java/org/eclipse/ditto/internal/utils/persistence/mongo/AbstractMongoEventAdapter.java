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

import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventRegistry;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
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

    private static final Predicate<JsonField> IS_REVISION = field -> field.getDefinition()
            .filter(EventsourcedEvent.JsonFields.REVISION::equals)
            .isPresent();

    @Nullable protected final ExtendedActorSystem system;
    protected final EventRegistry<T> eventRegistry;

    protected AbstractMongoEventAdapter(@Nullable final ExtendedActorSystem system,
            final EventRegistry<T> eventRegistry) {
        this.system = system;
        this.eventRegistry = eventRegistry;
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
        if (event instanceof Event) {
            final Event<?> theEvent = (Event<?>) event;
            final JsonSchemaVersion schemaVersion = theEvent.getImplementedSchemaVersion();
            final JsonObject jsonObject = performToJournalMigration(
                    theEvent.toJson(schemaVersion, IS_REVISION.negate())
            );
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
                final T result =
                        eventRegistry.parse(performFromJournalMigration(jsonObject), DittoHeaders.empty());
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
     * @param jsonObject the JsonObject representation of the {@link Event} to persist.
     * @return the adjusted/migrated JsonObject to store.
     */
    protected JsonObject performToJournalMigration(final JsonObject jsonObject) {
        return jsonObject;
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
