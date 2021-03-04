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
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

import akka.actor.ExtendedActorSystem;

/**
 * EventAdapter for {@link PolicyEvent}s persisted into akka-persistence event-journal.
 * Converts Event to MongoDB BSON objects and vice versa.
 */
public final class DefaultPolicyMongoEventAdapter extends AbstractPolicyMongoEventAdapter {

    private final Map<String, Function<JsonObject, JsonObject>> migrationMappings;

    public DefaultPolicyMongoEventAdapter(@Nullable final ExtendedActorSystem system) {
        super(system);
        migrationMappings = new HashMap<>();
    }

    @Override
    protected JsonObject performFromJournalMigration(final JsonObject jsonObject) {
        return migrateComplex(migratePayload(jsonObject));
    }

    @SuppressWarnings("squid:CallToDeprecatedMethod")
    private JsonObject migrateComplex(final JsonObject jsonObject) {
        return jsonObject.getValue(Event.JsonFields.ID)
                .map(migrationMappings::get)
                .map(migration -> migration.apply(jsonObject))
                .orElse(jsonObject);
    }

}
