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

import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.serializer.AbstractPostgresEventAdapter;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventSeq;

/**
 * PostgreSQL {@code EventAdapter} for {@link PolicyEvent}s persisted into the pekko-persistence event-journal. The JSONB
 * peer of {@link DefaultPolicyMongoEventAdapter}/{@link AbstractPolicyMongoEventAdapter}: it reuses the identical
 * serializer logic and the same manifest-keyed discard of removed legacy event types, differing only in the storage
 * envelope (JSONB text instead of BSON).
 */
public final class PolicyPostgresEventAdapter extends AbstractPostgresEventAdapter<PolicyEvent<?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyPostgresEventAdapter.class);

    /**
     * Event types that existed prior to the policy-import-model simplification (commit 7dd7eb22fa,
     * 2026-04-29) and were removed in favour of the unified {@code references}/{@code allowedAdditions}
     * model. Any journal row carrying one of these types is silently skipped during recovery so
     * clusters that wrote them under previous versions can roll forward without operator action.
     * Mirrors {@code AbstractPolicyMongoEventAdapter}.
     */
    private static final Set<String> LEGACY_DISCARDED_EVENT_TYPES = Set.of(
            "policies:importsAliasCreated",
            "policies:importsAliasModified",
            "policies:importsAliasDeleted",
            "policies:importsAliasSubjectCreated",
            "policies:importsAliasSubjectModified",
            "policies:importsAliasSubjectDeleted",
            "policies:importsAliasSubjectsModified",
            "policies:importsAliasesModified",
            "policies:importsAliasesDeleted",
            "policies:policyEntryAllowedImportAdditionsModified",
            "policies:policyImportEntriesAdditionsModified",
            "policies:policyImportEntryAdditionCreated",
            "policies:policyImportEntryAdditionModified",
            "policies:policyImportEntryAdditionDeleted"
    );

    public PolicyPostgresEventAdapter(final ExtendedActorSystem system) {
        super(system, GlobalEventRegistry.getInstance(), DefaultPolicyConfig.of(
                        DittoServiceConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()), "policies"))
                .getEventConfig());
    }

    @Override
    public EventSeq fromJournalJson(final JsonObject jsonObject, @Nullable final String manifest) {
        if (manifest != null && LEGACY_DISCARDED_EVENT_TYPES.contains(manifest)) {
            LOGGER.info("Discarding legacy policy event of removed type <{}> during journal recovery.",
                    manifest);
            return EventSeq.empty();
        }
        return super.fromJournalJson(jsonObject, manifest);
    }

}
