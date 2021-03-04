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
package org.eclipse.ditto.services.policies.persistence.serializer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.slf4j.LoggerFactory;

import akka.actor.ExtendedActorSystem;

/**
 * EventAdapter for {@link PolicyEvent}s persisted into akka-persistence event-journal.
 * Converts Event to MongoDB BSON objects and vice versa.
 */
public final class DefaultPolicyMongoEventAdapter extends AbstractPolicyMongoEventAdapter {

    private final Map<String, Function<JsonObject, JsonObject>> migrationMappings;

    public DefaultPolicyMongoEventAdapter(@Nullable final ExtendedActorSystem system) {
        super(LoggerFactory.getLogger(DefaultPolicyMongoEventAdapter.class), system);
        migrationMappings = new HashMap<>();
    }

    @Override
    protected Event createEventFrom(final JsonValue json) {
        final JsonObject jsonObject = json.asObject()
                .setValue(Event.JsonFields.REVISION.getPointer(), Event.DEFAULT_REVISION);
        return eventRegistry.parse(migrateComplex(migratePayload(jsonObject)), DittoHeaders.empty());
    }
    @SuppressWarnings("squid:CallToDeprecatedMethod")
    private JsonObject migrateComplex(final JsonObject jsonObject) {
        return jsonObject.getValue(Event.JsonFields.ID)
                .map(migrationMappings::get)
                .map(migration -> migration.apply(jsonObject))
                .orElse(jsonObject);
    }

}
