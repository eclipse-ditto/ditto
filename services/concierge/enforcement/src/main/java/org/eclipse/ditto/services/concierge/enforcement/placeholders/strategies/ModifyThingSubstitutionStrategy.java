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
package org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.HeaderBasedPlaceholderSubstitutionAlgorithm;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Handles substitution for ACL {@link org.eclipse.ditto.model.base.auth.AuthorizationSubject}s and
 * Policy {@link org.eclipse.ditto.model.policies.SubjectId}s
 * inside a {@link ModifyThing} command.
 */
final class ModifyThingSubstitutionStrategy extends AbstractTypedSubstitutionStrategy<ModifyThing> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModifyThingSubstitutionStrategy.class);

    ModifyThingSubstitutionStrategy() {
        super(ModifyThing.class);
    }

    @Override
    public WithDittoHeaders apply(final ModifyThing modifyThing,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm) {
        requireNonNull(modifyThing);
        requireNonNull(substitutionAlgorithm);

        final DittoHeaders dittoHeaders = modifyThing.getDittoHeaders();

        final JsonObject inlinePolicyJson = modifyThing.getInitialPolicy().orElse(null);
        final JsonObject substitutedInlinePolicyJson;

        if (inlinePolicyJson == null) {
            substitutedInlinePolicyJson = null;
        } else {
            substitutedInlinePolicyJson =
                    substituteInitialPolicy(inlinePolicyJson, substitutionAlgorithm, dittoHeaders);
        }

        final Thing existingThing = modifyThing.getThing();
        final Thing substitutedThing = substituteThing(existingThing, substitutionAlgorithm, dittoHeaders);

        if (existingThing.equals(substitutedThing) && Objects.equals(inlinePolicyJson, substitutedInlinePolicyJson)) {
            return modifyThing;
        } else {
            return ModifyThing.of(modifyThing.getEntityId(), substitutedThing, substitutedInlinePolicyJson,
                    modifyThing.getPolicyIdOrPlaceholder().orElse(null), dittoHeaders);
        }
    }

    private static JsonObject substituteInitialPolicy(final JsonObject initialPolicy,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm,
            final DittoHeaders dittoHeaders) {
        Policy existingPolicy;

        try {
            existingPolicy = PoliciesModelFactory.newPolicy(initialPolicy);
        } catch (final RuntimeException e) {
            // Just log to debug, error is handled somewhere else
            LOGGER.debug("Failed to parse initial policy.", e);
            return initialPolicy;
        }

        final Policy substitutedPolicy =
                substitutePolicy(existingPolicy, substitutionAlgorithm, dittoHeaders);
        return substitutedPolicy.toJson();
    }
}
