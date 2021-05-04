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
package org.eclipse.ditto.concierge.service.enforcement.placeholders.strategies;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.concierge.service.enforcement.placeholders.HeaderBasedPlaceholderSubstitutionAlgorithm;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Handles substitution for Policy {@link org.eclipse.ditto.policies.model.SubjectId}s
 * inside a {@link ModifyThing} command.
 */
final class ModifyThingSubstitutionStrategy extends AbstractTypedSubstitutionStrategy<ModifyThing> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModifyThingSubstitutionStrategy.class);

    ModifyThingSubstitutionStrategy() {
        super(ModifyThing.class);
    }

    @Override
    public DittoHeadersSettable<?> apply(final ModifyThing modifyThing,
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

        if (Objects.equals(inlinePolicyJson, substitutedInlinePolicyJson)) {
            return modifyThing;
        } else {
            return ModifyThing.of(modifyThing.getEntityId(), modifyThing.getThing(), substitutedInlinePolicyJson,
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
