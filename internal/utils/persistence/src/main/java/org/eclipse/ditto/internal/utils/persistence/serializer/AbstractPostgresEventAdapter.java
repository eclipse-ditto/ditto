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
package org.eclipse.ditto.internal.utils.persistence.serializer;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventRegistry;
import org.eclipse.ditto.internal.utils.persistence.api.config.EventConfig;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.EventSerializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventAdapter;
import org.apache.pekko.persistence.journal.EventSeq;
import org.apache.pekko.persistence.journal.Tagged;

/**
 * Abstract event adapter for persisting Ditto {@link Event}s into PostgreSQL.
 * <p>
 * This is the JSONB peer of {@code AbstractMongoEventAdapter}: the pure domain&harr;{@link JsonObject} serialization and
 * journal-migration logic lives in {@link EventSerializer}, which this class extends. This class adds <em>only</em> the
 * JSONB envelope. {@link #toJournal(Object)} renders the serializer's {@link JsonObject} to JSON <em>text</em> (the
 * value the {@code PostgresJournal} binds to a {@code ::jsonb} column) and attaches the journal tags via a Pekko
 * {@link Tagged} so the high-water-mark/ping path can persist them. {@link #fromJournal(Object, String)} decodes the
 * stored JSONB representation (the r2dbc PostgreSQL driver surfaces JSONB as {@code String}, {@code byte[]} or an
 * already-parsed {@link JsonObject}) back to a {@link JsonObject} and feeds the <em>same</em> serializer the MongoDB
 * adapter uses, so the manifest-keyed up-/down-cast migration policy is shared and only the storage envelope differs.
 * <p>
 * Without this adapter and its {@code event-adapter-bindings} the journal would receive Pekko's identity adapter:
 * {@code PostgresJournal} would be handed a raw domain {@link Event} (not JSON-bindable, write fails) and recovery would
 * hand the persistent actor a bare {@code String} its {@code match(getEventClass())} never matches (events silently
 * dropped). Both failures are invisible to tests that persist a hand-built {@link JsonObject}; this adapter closes that
 * seam.
 *
 * @param <T> the event type to serialize.
 */
public abstract class AbstractPostgresEventAdapter<T extends Event<?>> extends EventSerializer<T>
        implements EventAdapter {

    protected AbstractPostgresEventAdapter(@Nullable final ExtendedActorSystem system,
            final EventRegistry<T> eventRegistry, final EventConfig eventConfig) {
        super(system, eventRegistry, eventConfig);
    }

    @Override
    public Object toJournal(final Object event) {
        if (event instanceof Event<?> theEvent) {
            final JsonObject jsonObject = toJournalJson(theEvent);
            final Set<String> tags = getJournalTags(theEvent);
            // JSONB envelope: the column value is the serializer JsonObject rendered as JSON text; binding to a
            // ::jsonb parameter is the PostgresJournal's job. The tags travel in the Pekko Tagged so the journal
            // persists them (PersistencePingActor wakes always-alive entities by journal tag).
            return new Tagged(jsonObject.toString(), tags);
        } else {
            throw new IllegalArgumentException("Unable to toJournal a non-'Event' object! Was: " + event.getClass());
        }
    }

    @Override
    public EventSeq fromJournal(final Object event, final String manifest) {
        return fromJournalJson(decodeJsonb(event), manifest);
    }

    /**
     * Decodes the raw journal payload as loaded from a JSONB column to a {@link JsonObject}. Accepts the shapes the
     * r2dbc PostgreSQL driver can surface for JSONB: a {@link JsonObject} (already parsed), a {@link CharSequence}
     * (JSONB text) or {@code byte[]} (UTF-8 JSONB bytes).
     *
     * @param rawEvent the raw loaded journal payload.
     * @return the decoded JsonObject.
     * @throws IllegalArgumentException if {@code rawEvent} is not a JSONB-decodable type.
     */
    private static JsonObject decodeJsonb(final Object rawEvent) {
        if (rawEvent instanceof JsonObject jsonObject) {
            return jsonObject;
        }
        if (rawEvent instanceof CharSequence charSequence) {
            return JsonFactory.newObject(charSequence.toString());
        }
        if (rawEvent instanceof byte[] bytes) {
            return JsonFactory.newObject(new String(bytes, StandardCharsets.UTF_8));
        }
        throw new IllegalArgumentException("Unable to fromJournal a non JSONB-decodable object (expected JsonObject, "
                + "String or byte[])! Was: " + (rawEvent != null ? rawEvent.getClass() : "null"));
    }

}
