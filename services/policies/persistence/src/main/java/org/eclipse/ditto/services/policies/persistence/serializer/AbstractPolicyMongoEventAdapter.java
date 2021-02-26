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

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.utils.persistence.mongo.AbstractMongoEventAdapter;
import org.eclipse.ditto.signals.events.base.GlobalEventRegistry;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

import akka.actor.ExtendedActorSystem;

/**
 * Abstract base EventAdapter for {@link PolicyEvent}s persisted into akka-persistence event-journal.
 * Converts Events to MongoDB BSON objects and vice versa.
 */
public abstract class AbstractPolicyMongoEventAdapter extends AbstractMongoEventAdapter<PolicyEvent<?>> {

    // JSON field containing the event's payload.
    private static final JsonFieldDefinition<JsonObject> PAYLOAD =
            JsonFactory.newJsonObjectFieldDefinition("payload", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    protected static final JsonFieldDefinition<JsonObject> POLICY_ENTRIES =
            JsonFactory.newJsonObjectFieldDefinition("policy/entries", FieldType.SPECIAL, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    protected AbstractPolicyMongoEventAdapter(@Nullable final ExtendedActorSystem system) {
        super(system, GlobalEventRegistry.getInstance());
    }

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
