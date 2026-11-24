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
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.EventSerializer;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Backend-neutral journal serializer for {@link PolicyEvent}s, shared by the policy event adapters of both backends.
 * It discards journal rows whose manifest names a policy event type that was removed (see {@link #discards(String)})
 * and logs one INFO line per discarded row; everything else is the behaviour of {@link EventSerializer}.
 */
public class PolicyEventSerializer extends EventSerializer<PolicyEvent<?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyEventSerializer.class);

    /**
     * Event types that existed prior to the policy-import-model simplification (commit 7dd7eb22fa,
     * 2026-04-29) and were removed in favour of the unified {@code references}/{@code allowedAdditions}
     * model. Any journal row carrying one of these types is silently skipped during recovery so
     * clusters that wrote them under previous versions can roll forward without operator action.
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

    /**
     * Constructs a new {@code PolicyEventSerializer}.
     *
     * @param system the actor system whose config provides the policies event config.
     */
    public PolicyEventSerializer(final ExtendedActorSystem system) {
        super(system, GlobalEventRegistry.getInstance(), DefaultPolicyConfig.of(
                        DittoServiceConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()), "policies"))
                .getEventConfig());
    }

    @Override
    public EventSeq fromJournalJson(final JsonObject jsonObject, @Nullable final String manifest) {
        // Drop journal rows whose manifest matches a removed legacy event type before they reach
        // the parser. The parent's catch-all logs every parse failure as an ERROR; here we emit a
        // single INFO per legacy type so upgrades on clusters that emitted these events are
        // observable but not noisy. The persistence layer provides the manifest from the stored
        // row's manifest field; for the rare case of a row without manifest the parent's path will
        // throw JsonTypeNotParsableException which is caught and logged as ERROR. This lives in the
        // backend-neutral serializer layer so both the MongoDB and PostgreSQL envelopes reuse it.
        if (discards(manifest)) {
            LOGGER.info("Discarding legacy policy event of removed type <{}> during journal recovery.",
                    manifest);
            return EventSeq.empty();
        }
        return super.fromJournalJson(jsonObject, manifest);
    }

    /**
     * Tells whether a journal row with the given manifest is discarded during recovery because it names a policy event
     * type that was removed.
     *
     * @param manifest the stored Pekko manifest (event type), or {@code null} if unavailable.
     * @return {@code true} if the manifest names a removed legacy policy event type.
     */
    public boolean discards(@Nullable final String manifest) {
        return manifest != null && LEGACY_DISCARDED_EVENT_TYPES.contains(manifest);
    }

}
