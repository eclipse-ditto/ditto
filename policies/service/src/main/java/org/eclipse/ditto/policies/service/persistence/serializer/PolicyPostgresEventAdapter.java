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

package org.eclipse.ditto.policies.service.persistence.serializer;

import java.util.Set;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventSeq;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.serializer.AbstractPostgresEventAdapter;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;

/**
 * PostgreSQL {@code EventAdapter} for {@link PolicyEvent}s persisted into the pekko-persistence event-journal: stores
 * the journal JSON as JSONB text; the domain hooks (discard of removed legacy policy event types) are in
 * {@link PolicyEventSerializer}.
 */
public final class PolicyPostgresEventAdapter extends AbstractPostgresEventAdapter<PolicyEvent<?>> {

    private final PolicyEventSerializer serializer;

    public PolicyPostgresEventAdapter(final ExtendedActorSystem system) {
        super(system, GlobalEventRegistry.getInstance(), DefaultPolicyConfig.of(
                        DittoServiceConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()), "policies"))
                .getEventConfig());
        serializer = new PolicyEventSerializer(system);
    }

    @Override
    public JsonObject toJournalJson(final Event<?> event) {
        return serializer.toJournalJson(event);
    }

    @Override
    public Set<String> getJournalTags(final Event<?> event) {
        return serializer.getJournalTags(event);
    }

    @Override
    public EventSeq fromJournalJson(final JsonObject jsonObject, @Nullable final String manifest) {
        return serializer.fromJournalJson(jsonObject, manifest);
    }

    @Override
    public EventSeq fromJournalJson(final JsonObject jsonObject) {
        return serializer.fromJournalJson(jsonObject);
    }

}
