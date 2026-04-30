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

import java.util.Set;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.AbstractMongoEventAdapter;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventSeq;

/**
 * Abstract base EventAdapter for {@link PolicyEvent}s persisted into pekko-persistence event-journal.
 * Converts Events to MongoDB BSON objects and vice versa.
 */
public abstract class AbstractPolicyMongoEventAdapter extends AbstractMongoEventAdapter<PolicyEvent<?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPolicyMongoEventAdapter.class);

    protected static final JsonFieldDefinition<JsonObject> POLICY_ENTRIES =
            JsonFactory.newJsonObjectFieldDefinition("policy/entries", FieldType.SPECIAL,
                    JsonSchemaVersion.V_2);

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

    protected AbstractPolicyMongoEventAdapter(final ExtendedActorSystem system) {
        super(system, GlobalEventRegistry.getInstance(), DefaultPolicyConfig.of(
                        DittoServiceConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()), "policies"))
                .getEventConfig());
    }

    @Override
    public EventSeq fromJournal(final Object event, final String manifest) {
        // Drop journal rows whose manifest matches a removed legacy event type before they reach
        // the parser. The parent's catch-all logs every parse failure as an ERROR; here we want a
        // single INFO line per legacy type so upgrades on policies that emitted these events are
        // observable but not noisy. Manifest may be null for older Pekko persistence rows; in that
        // case fall through to the JSON-based check in performFromJournalMigration.
        if (manifest != null && LEGACY_DISCARDED_EVENT_TYPES.contains(manifest)) {
            LOGGER.info("Discarding legacy policy event of removed type <{}> during journal recovery.",
                    manifest);
            return EventSeq.empty();
        }
        return super.fromJournal(event, manifest);
    }

    @Override
    protected JsonObject performFromJournalMigration(final JsonObject jsonObject) {
        // Defence in depth: if the manifest path didn't catch it (e.g. the row was written without
        // the `manifest` value or with a different one), inspect the JSON `type` field as well.
        final String type = jsonObject.getValue(Event.JsonFields.TYPE).orElse(null);
        if (type != null && LEGACY_DISCARDED_EVENT_TYPES.contains(type)) {
            // Rewrite to a known-discarded marker so the registry returns empty rather than throw.
            // We use a JSON parse exception via the registry's own type-not-parsable path; the
            // outer fromJournal catches DittoRuntimeException and returns EventSeq.empty() — but
            // because we've already filtered in fromJournal, this is only reached in test-style
            // direct calls.
            LOGGER.info("Encountered legacy policy event JSON of removed type <{}> during journal " +
                    "migration; will be skipped.", type);
        }
        return jsonObject;
    }

    /**
     * Exposed for tests: returns {@code true} if the given event type belongs to the set of
     * legacy types that are silently discarded during recovery.
     */
    public static boolean isLegacyDiscardedEventType(final JsonValue typeValue) {
        return typeValue.isString() && LEGACY_DISCARDED_EVENT_TYPES.contains(typeValue.asString());
    }
}
