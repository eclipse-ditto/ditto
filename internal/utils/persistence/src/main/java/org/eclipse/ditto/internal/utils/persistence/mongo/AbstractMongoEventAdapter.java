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

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventRegistry;
import org.eclipse.ditto.internal.utils.persistence.api.config.EventConfig;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.EventSerializer;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventAdapter;
import org.apache.pekko.persistence.journal.EventSeq;
import org.apache.pekko.persistence.journal.Tagged;

/**
 * Abstract event adapter for persisting Ditto {@link Event}s into MongoDB.
 * <p>
 * The pure domain&harr;{@link JsonObject} serialization and journal-migration logic lives in {@link EventSerializer},
 * which this class extends. This class adds <em>only</em> the BSON envelope: {@link #toJournal(Object)} encodes the
 * serializer's JsonObject to BSON and attaches the tags, and {@link #fromJournal(Object, String)} decodes a stored
 * {@link BsonValue} back to a JsonObject before feeding the serializer. A PostgreSQL event adapter reuses the same
 * serializer with a JSONB envelope instead.
 */
public abstract class AbstractMongoEventAdapter<T extends Event<?>> extends EventSerializer<T>
        implements EventAdapter {

    protected AbstractMongoEventAdapter(final ExtendedActorSystem system,
            final EventRegistry<T> eventRegistry, final EventConfig eventConfig) {
        super(system, eventRegistry, eventConfig);
    }

    @Override
    public Object toJournal(final Object event) {
        if (event instanceof Event<?> theEvent) {
            final JsonObject jsonObject = toJournalJson(theEvent);
            final BsonDocument bson = DittoBsonJson.getInstance().parse(jsonObject);
            final Set<String> tags = getJournalTags(theEvent);
            return new Tagged(bson, tags);
        } else {
            throw new IllegalArgumentException("Unable to toJournal a non-'Event' object! Was: " + event.getClass());
        }
    }

    @Override
    public EventSeq fromJournal(final Object event, final String manifest) {
        if (event instanceof BsonValue bsonValue) {
            final JsonValue jsonValue = DittoBsonJson.getInstance().serialize(bsonValue);
            return fromJournalJson(jsonValue.asObject(), manifest);
        } else {
            throw new IllegalArgumentException(
                    "Unable to fromJournal a non-'BsonValue' object! Was: " + event.getClass());
        }
    }

}
