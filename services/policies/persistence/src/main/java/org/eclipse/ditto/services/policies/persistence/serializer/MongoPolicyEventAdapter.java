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
package org.eclipse.ditto.services.policies.persistence.serializer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.WithType;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.base.EventRegistry;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.policies.PolicyEventRegistry;

import com.mongodb.DBObject;
import com.mongodb.util.DittoBsonJSON;

import akka.actor.ExtendedActorSystem;
import akka.persistence.journal.EventAdapter;
import akka.persistence.journal.EventSeq;
import akka.persistence.journal.Tagged;

/**
 * EventAdapter for {@link PolicyEvent}s persisted into akka-persistence event-journal. Converts Event to MongoDB BSON
 * objects and vice versa.
 */
public final class MongoPolicyEventAdapter implements EventAdapter {

    private static final Predicate<JsonField> IS_REVISION = field -> field.getDefinition()
            .map(Event.JsonFields.REVISION::equals)
            .orElse(false);

    // JSON field containing the event's payload.
    private static final JsonFieldDefinition<JsonObject> PAYLOAD =
            JsonFactory.newJsonObjectFieldDefinition("payload", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final Map<String, Function<JsonObject, JsonObject>> migrationMappings;
    private final ExtendedActorSystem system;
    private final EventRegistry<PolicyEvent> eventRegistry;

    public MongoPolicyEventAdapter(final ExtendedActorSystem system) {
        this.system = system;
        eventRegistry = PolicyEventRegistry.newInstance();
        migrationMappings = new HashMap<>();
    }

    @Override
    public String manifest(final Object event) {
        if (event instanceof PolicyEvent) {
            return ((WithType) event).getType();
        } else {
            throw new IllegalArgumentException(
                    "Unable to create manifest for a non-'PolicyEvent' object! Was: " + event.getClass());
        }
    }

    @Override
    public Object toJournal(final Object event) {
        if (event instanceof PolicyEvent) {
            final PolicyEvent<?> theEvent = (PolicyEvent) event;
            final JsonSchemaVersion schemaVersion = theEvent.getImplementedSchemaVersion();
            final JsonObject jsonObject =
                    theEvent.toJson(schemaVersion, IS_REVISION.negate().and(FieldType.regularOrSpecial()));
            final Object bson = DittoBsonJSON.parse(jsonObject.toString());
            final Set<String> readSubjects = theEvent.getDittoHeaders().getReadSubjects();
            return new Tagged(bson, readSubjects);
        } else {
            throw new IllegalArgumentException(
                    "Unable to toJournal a non-'PolicyEvent' object! Was: " + event.getClass());
        }
    }

    @Override
    public EventSeq fromJournal(final Object event, final String manifest) {
        if (event instanceof DBObject) {
            final DBObject dbObject = (DBObject) event;
            return EventSeq.single(tryToCreateEventFrom(DittoBsonJSON.serialize(dbObject)));
        } else {
            throw new IllegalArgumentException(
                    "Unable to fromJournal a non-'DBObject' object! Was: " + event.getClass());
        }
    }

    private PolicyEvent tryToCreateEventFrom(final String json) {
        try {
            return createEventFrom(json);
        } catch (final JsonParseException | DittoRuntimeException e) {
            if (system != null) {
                system.log().error(e, "Could not deserialize PolicyEvent JSON: '{}'", json);
            } else {
                System.err.println("Could not deserialize PolicyEvent JSON: '" + json + "': " + e.getMessage());
            }
            return null;
        }
    }

    private PolicyEvent createEventFrom(final String json) {
        final JsonObject jsonObject = JsonFactory.newObject(json)
                .setValue(Event.JsonFields.REVISION.getPointer(), Event.DEFAULT_REVISION);

        return eventRegistry.parse(migrateComplex(migratePayload(jsonObject)), DittoHeaders.empty());
    }

    /**
     * A "payload" object was wrapping the events payload until the introduction of "cr-commands 1.0.0". This field has
     * to be used as fallback for already persisted events with "things-model" < 3.0.0. Removing this workaround is
     * possible if we are sure that no "old" events are ever loaded again!
     */
    private static JsonObject migratePayload(final JsonObject jsonObject) {
        return jsonObject.getValue(PAYLOAD)
                .map(obj -> jsonObject.remove(PAYLOAD.getPointer()).setAll(obj))
                .orElse(jsonObject);
    }

    private JsonObject migrateComplex(final JsonObject jsonObject) {
        return jsonObject.getValue(Event.JsonFields.ID)
                .map(migrationMappings::get)
                .map(migration -> migration.apply(jsonObject))
                .orElse(jsonObject);
    }

}
