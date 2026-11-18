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
package org.eclipse.ditto.internal.utils.persistence.mongo.adapters;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventSeq;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.EventSerializer;
import org.eclipse.ditto.json.JsonObject;

/**
 * Test-only {@link EventSerializer} that returns canned values and records every call it receives.
 */
public final class RecordingEventSerializer extends EventSerializer<Event<?>> {

    static final JsonObject CANNED_JSON = JsonObject.newBuilder()
            .set("type", "stub.events:stubbed")
            .set("revision", 3L)
            .set("nested", JsonObject.newBuilder().set("text", "\u00e4\u20ac \"quoted\"").build())
            .build();
    static final Set<String> TAGS = Set.of("stub-tag-a", "stub-tag-b");
    static final String MANIFEST = "stub.events:manifest";
    static final EventSeq RESULT = EventSeq.single("recovered-by-stub");

    final List<String> calls = new ArrayList<>();
    final List<JsonObject> receivedJson = new ArrayList<>();
    final List<String> receivedManifests = new ArrayList<>();

    /**
     * The constructor the delegating adapters call by class name.
     *
     * @param system the actor system.
     */
    public RecordingEventSerializer(final ExtendedActorSystem system) {
        super(system, null, null);
    }

    RecordingEventSerializer() {
        super(null, null, null);
    }

    @Override
    public String manifest(final Object event) {
        calls.add("manifest");
        return MANIFEST;
    }

    @Override
    public JsonObject toJournalJson(final Event<?> event) {
        calls.add("toJournalJson");
        return CANNED_JSON;
    }

    @Override
    public Set<String> getJournalTags(final Event<?> event) {
        calls.add("getJournalTags");
        return TAGS;
    }

    @Override
    public EventSeq fromJournalJson(final JsonObject jsonObject, @Nullable final String manifest) {
        calls.add("fromJournalJson");
        receivedJson.add(jsonObject);
        receivedManifests.add(manifest);
        return RESULT;
    }

}
