/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import java.util.Collections;
import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.thingsearch.persistence.write.EventToPersistenceStrategy;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * Abstract base class for the Mongo implementations of {@link EventToPersistenceStrategy}.
 *
 * @param <T> Type of the {@link ThingEvent} for which the strategy is created.
 */
public abstract class MongoEventToPersistenceStrategy<T extends ThingEvent> implements EventToPersistenceStrategy<T,
        Bson, PolicyUpdate> {

    /**
     * Get the list of {@link PolicyUpdate}s needed to persist the {@code event}.
     *
     * @param event The event.
     * @param policyEnforcer The policy enforcer.
     * @return The policy updates.
     */
    @Override
    public List<PolicyUpdate> policyUpdates(final T event,
            final Enforcer policyEnforcer) {
        // implemented since not all strategies need policy updates.
        return Collections.emptyList();
    }

    /**
     * Check if an event with {@code jsonSchemaVersion} is relevant for policies.
     *
     * @param jsonSchemaVersion the schema version.
     * @return true if the version is relevant for policies.
     */
    boolean isPolicyRevelant(final JsonSchemaVersion jsonSchemaVersion) {
        return jsonSchemaVersion.toInt() > JsonSchemaVersion.V_1.toInt();
    }

}
