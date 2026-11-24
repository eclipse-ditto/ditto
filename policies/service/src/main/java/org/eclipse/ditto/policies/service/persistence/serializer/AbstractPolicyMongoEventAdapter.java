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

package org.eclipse.ditto.policies.service.persistence.serializer;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.AbstractMongoEventAdapter;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventSeq;

/**
 * Abstract base EventAdapter for {@link PolicyEvent}s persisted into pekko-persistence event-journal.
 * Converts Events to MongoDB BSON objects and vice versa. Subclasses may override the journal migration hooks; journal
 * rows of removed legacy policy event types are discarded during recovery as {@link PolicyEventSerializer} decides.
 */
public abstract class AbstractPolicyMongoEventAdapter extends AbstractMongoEventAdapter<PolicyEvent<?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPolicyMongoEventAdapter.class);

    protected static final JsonFieldDefinition<JsonObject> POLICY_ENTRIES =
            JsonFactory.newJsonObjectFieldDefinition("policy/entries", FieldType.SPECIAL,
                    JsonSchemaVersion.V_2);

    private final PolicyEventSerializer policyEventSerializer;

    protected AbstractPolicyMongoEventAdapter(final ExtendedActorSystem system) {
        super(system, GlobalEventRegistry.getInstance(), DefaultPolicyConfig.of(
                        DittoServiceConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()), "policies"))
                .getEventConfig());
        policyEventSerializer = new PolicyEventSerializer(system);
    }

    @Override
    public EventSeq fromJournalJson(final JsonObject jsonObject, @Nullable final String manifest) {
        if (policyEventSerializer.discards(manifest)) {
            LOGGER.info("Discarding legacy policy event of removed type <{}> during journal recovery.", manifest);
            return EventSeq.empty();
        }
        return super.fromJournalJson(jsonObject, manifest);
    }
}
