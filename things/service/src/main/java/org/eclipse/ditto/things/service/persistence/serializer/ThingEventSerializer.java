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
package org.eclipse.ditto.things.service.persistence.serializer;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.EventSerializer;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.eclipse.ditto.things.service.common.config.DefaultThingConfig;

/**
 * Backend-neutral journal serializer for {@link ThingEvent}s. Strips the inlined policy from the journal payload; the
 * backend event adapters add only the storage envelope.
 */
public final class ThingEventSerializer extends EventSerializer<ThingEvent<?>> {

    private static final JsonPointer POLICY_IN_THING_EVENT_PAYLOAD = ThingEvent.JsonFields.THING.getPointer()
            .append(JsonPointer.of(Policy.INLINED_FIELD_NAME));

    private static final JsonPointer POLICY_IN_THING_MERGED_VALUE_PAYLOAD =
            ThingMerged.JsonFields.JSON_VALUE.getPointer()
                    .append(JsonPointer.of(Policy.INLINED_FIELD_NAME));

    /**
     * Constructs a new {@code ThingEventSerializer}.
     *
     * @param system the actor system whose config provides the things event config.
     */
    public ThingEventSerializer(final ExtendedActorSystem system) {
        super(system, GlobalEventRegistry.getInstance(), DefaultThingConfig.of(
                DittoServiceConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()), "things")
        ).getEventConfig());
    }

    @Override
    protected JsonObjectBuilder performToJournalMigration(final Event<?> event, final JsonObject jsonObject) {
        if (event instanceof ThingMerged thingMerged && thingMerged.getResourcePath().isEmpty()) {
            return super.performToJournalMigration(event, jsonObject)
                    .remove(POLICY_IN_THING_MERGED_VALUE_PAYLOAD); // remove the policy entries from thing merged payload
        } else {
            return super.performToJournalMigration(event, jsonObject)
                    .remove(POLICY_IN_THING_EVENT_PAYLOAD); // remove the policy entries from thing event payload
        }
    }

}
