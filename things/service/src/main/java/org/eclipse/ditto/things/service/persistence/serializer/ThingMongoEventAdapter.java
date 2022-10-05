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
package org.eclipse.ditto.things.service.persistence.serializer;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.internal.utils.persistence.mongo.AbstractMongoEventAdapter;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import akka.actor.ExtendedActorSystem;

/**
 * EventAdapter for {@link Event}s persisted into akka-persistence event-journal. Converts Event to MongoDB
 * BSON objects and vice versa.
 */
public final class ThingMongoEventAdapter extends AbstractMongoEventAdapter<ThingEvent<?>> {

    private static final JsonPointer POLICY_IN_THING_EVENT_PAYLOAD = ThingEvent.JsonFields.THING.getPointer()
            .append(JsonPointer.of(Policy.INLINED_FIELD_NAME));

    public ThingMongoEventAdapter(@Nullable final ExtendedActorSystem system) {
        super(system, GlobalEventRegistry.getInstance());
    }

    @Override
    protected JsonObject performToJournalMigration(final JsonObject jsonObject) {
        return jsonObject
                .remove(POLICY_IN_THING_EVENT_PAYLOAD); // remove the policy entries from thing event payload
    }

    @Override
    public Object toJournal(final Object event) {
        if (event instanceof Event<?> theEvent) {
            final JsonSchemaVersion schemaVersion = theEvent.getImplementedSchemaVersion();
            final JsonObject jsonObject =
                    theEvent.toJson(schemaVersion, FieldType.regularOrSpecial())
                            // remove the policy entries from thing event payload
                            .remove(POLICY_IN_THING_EVENT_PAYLOAD);
            final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
            return dittoBsonJson.parse(jsonObject);
        } else {
            throw new IllegalArgumentException("Unable to toJournal a non-'Event' object! Was: " + event.getClass());
        }
    }

}
