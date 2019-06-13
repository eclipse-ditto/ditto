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

package org.eclipse.ditto.services.policies.persistence.serializer;

import java.text.MessageFormat;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bson.BsonValue;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.signals.base.WithType;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.base.GlobalEventRegistry;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.slf4j.Logger;

import akka.actor.ExtendedActorSystem;
import akka.persistence.journal.EventAdapter;
import akka.persistence.journal.EventSeq;
import akka.persistence.journal.Tagged;

public abstract class AbstractPolicyMongoEventAdapter implements EventAdapter {

    private final Logger logger;

    private static final Predicate<JsonField> IS_REVISION = field -> field.getDefinition()
            .map(Event.JsonFields.REVISION::equals)
            .orElse(false);

    // JSON field containing the event's payload.
    private static final JsonFieldDefinition<JsonObject> PAYLOAD =
            JsonFactory.newJsonObjectFieldDefinition("payload", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    protected static final JsonFieldDefinition<JsonObject> POLICY_ENTRIES =
            JsonFactory.newJsonObjectFieldDefinition("policy/entries", FieldType.SPECIAL, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    protected final GlobalEventRegistry eventRegistry;
    @Nullable private final ExtendedActorSystem system;

    protected AbstractPolicyMongoEventAdapter(final Logger logger, @Nullable final ExtendedActorSystem system) {
        this.logger = logger;
        this.system = system;
        eventRegistry = GlobalEventRegistry.getInstance();
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
            final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
            final Object bson = dittoBsonJson.parse(jsonObject);
            final Set<String> readSubjects = calculateReadSubjects(theEvent);
            return new Tagged(bson, readSubjects);
        } else {
            throw new IllegalArgumentException(
                    "Unable to toJournal a non-'PolicyEvent' object! Was: " + event.getClass());
        }
    }

    private static Set<String> calculateReadSubjects(final Event<?> theEvent) {
        return theEvent.getDittoHeaders().getReadSubjects().stream()
                .map(rs -> "rs:" + rs)
                .collect(Collectors.toSet());
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
            final String message = MessageFormat.format("Could not deserialize PolicyEvent JSON: ''{0}''", json);
            if (system != null) {
                system.log().error(e, message);
            } else {
                logger.error(message, e);
            }
            return null;
        }
    }

    protected abstract Event createEventFrom(final JsonValue json);

    /**
     * A "payload" object was wrapping the events payload until the introduction of "cr-commands 1.0.0". This field has
     * to be used as fallback for already persisted events with "things-model" &lt; 3.0.0. Removing this workaround is
     * possible if we are sure that no "old" events are ever loaded again!
     *
     * @param jsonObject the jsonObject to be migrated.
     * @return the migrated jsonObject.
     */
    protected static JsonObject migratePayload(final JsonObject jsonObject) {
        return jsonObject.getValue(PAYLOAD)
                .map(obj -> jsonObject.remove(PAYLOAD.getPointer()).setAll(obj))
                .orElse(jsonObject);
    }

}
