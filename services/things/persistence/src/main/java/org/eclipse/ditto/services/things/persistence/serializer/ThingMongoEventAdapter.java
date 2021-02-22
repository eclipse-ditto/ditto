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
package org.eclipse.ditto.services.things.persistence.serializer;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.bson.BsonValue;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.base.GlobalEventRegistry;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ExtendedActorSystem;
import akka.persistence.journal.EventAdapter;
import akka.persistence.journal.EventSeq;

/**
 * EventAdapter for {@link Event}s persisted into akka-persistence event-journal. Converts Event to MongoDB
 * BSON objects and vice versa.
 */
public final class ThingMongoEventAdapter implements EventAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThingMongoEventAdapter.class);

    private static final Predicate<JsonField> IS_REVISION = field -> field.getDefinition()
            .map(definition -> Objects.equals(definition, Event.JsonFields.REVISION))
            .orElse(false);

    private static final JsonPointer POLICY_IN_THING_EVENT_PAYLOAD = ThingEvent.JsonFields.THING.getPointer()
            .append(JsonPointer.of(Policy.INLINED_FIELD_NAME));

    private final GlobalEventRegistry eventRegistry;

    public ThingMongoEventAdapter(@Nullable final ExtendedActorSystem system) {
        eventRegistry = GlobalEventRegistry.getInstance();
    }

    @Override
    public String manifest(final Object event) {
        if (event instanceof Event) {
            return ((Event) event).getType();
        } else {
            throw new IllegalArgumentException(
                    "Unable to create manifest for a non-'Event' object! Was: " + event.getClass());
        }
    }

    @Override
    public Object toJournal(final Object event) {
        if (event instanceof Event) {
            final Event<?> theEvent = (Event) event;
            final JsonSchemaVersion schemaVersion = theEvent.getImplementedSchemaVersion();
            final JsonObject jsonObject =
                    theEvent.toJson(schemaVersion, IS_REVISION.negate().and(FieldType.regularOrSpecial())) //
                            // remove the policy entries from thing event payload
                            .remove(POLICY_IN_THING_EVENT_PAYLOAD);
            final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
            return dittoBsonJson.parse(jsonObject);
        } else {
            throw new IllegalArgumentException("Unable to toJournal a non-'Event' object! Was: " + event.getClass());
        }
    }

    @Override
    public EventSeq fromJournal(final Object event, final String manifest) {
        if (event instanceof BsonValue) {
            return EventSeq.single(tryToCreateEventFrom(DittoBsonJson.getInstance().serialize((BsonValue) event)));
        } else {
            throw new IllegalArgumentException(
                    "Unable to fromJournal a non-'BsonValue' object! Was: " + event.getClass());
        }
    }

    @Nullable
    private Event tryToCreateEventFrom(final JsonValue json) {
        try {
            return createEventFrom(json);
        } catch (final JsonParseException | DittoRuntimeException e) {
            final String message = MessageFormat.format("Could not deserialize ThingEvent JSON: ''{0}''", json);
            LOGGER.error(message, e);
            return null;
        }
    }

    private Event createEventFrom(final JsonValue json) {
        final JsonObject jsonObject = json.asObject()
                .setValue(Event.JsonFields.REVISION.getPointer(), Event.DEFAULT_REVISION);
        return eventRegistry.parse(jsonObject, DittoHeaders.empty());
    }

}
