/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.enforcement.pre;

import static java.util.Objects.requireNonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.enforcement.placeholders.AbstractPlaceholderSubstitutionPreEnforcer;
import org.eclipse.ditto.policies.enforcement.placeholders.HeaderBasedPlaceholderSubstitutionAlgorithm;
import org.eclipse.ditto.policies.enforcement.placeholders.strategies.SubstitutionStrategyRegistry;

import akka.actor.ActorSystem;

/**
 * Things specific Pre-Enforcer which applies substitution of placeholders on a thing command
 * (subtype of {@link org.eclipse.ditto.base.model.signals.Signal}) based on its {@link DittoHeaders}.
 */
public final class ThingsPlaceholderSubstitutionPreEnforcer extends AbstractPlaceholderSubstitutionPreEnforcer {

    /**
     * Constructs a new instance of ThingsPlaceholderSubstitutionPreEnforcer extension.
     *
     * @param actorSystem the actor system in which to load the extension.
     */
    @SuppressWarnings("unused")
    public ThingsPlaceholderSubstitutionPreEnforcer(final ActorSystem actorSystem) {
        super(
                HeaderBasedPlaceholderSubstitutionAlgorithm.newInstance(createDefaultReplacementDefinitions()),
                ThingSubstitutionStrategyRegistry.newInstance()
        );
    }

    private ThingsPlaceholderSubstitutionPreEnforcer(
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm,
            final SubstitutionStrategyRegistry substitutionStrategyRegistry) {
        super(substitutionAlgorithm, substitutionStrategyRegistry);
    }

    /**
     * Creates a new instance with default replacement definitions.
     *
     * @return the created instance.
     * @see #newExtendedInstance(java.util.Map)
     */
    public static ThingsPlaceholderSubstitutionPreEnforcer newInstance() {
        final Map<String, Function<DittoHeaders, String>> defaultReplacementDefinitions =
                createDefaultReplacementDefinitions();

        return createInstance(defaultReplacementDefinitions);
    }

    /**
     * Creates a new instance with default replacement definitions, extended with
     * {@code additionalReplacementDefinitions}.
     *
     * @param additionalReplacementDefinitions the additional replacement definitions.
     * @return the created instance.
     * @see #newInstance()
     */
    public static ThingsPlaceholderSubstitutionPreEnforcer newExtendedInstance(
            final Map<String, Function<DittoHeaders, String>> additionalReplacementDefinitions) {
        requireNonNull(additionalReplacementDefinitions);

        final Map<String, Function<DittoHeaders, String>> defaultReplacementDefinitions =
                createDefaultReplacementDefinitions();

        final Map<String, Function<DittoHeaders, String>> allReplacementDefinitions =
                new LinkedHashMap<>();
        allReplacementDefinitions.putAll(defaultReplacementDefinitions);
        allReplacementDefinitions.putAll(additionalReplacementDefinitions);

        return createInstance(allReplacementDefinitions);
    }

    private static ThingsPlaceholderSubstitutionPreEnforcer createInstance(
            final Map<String, Function<DittoHeaders, String>> replacementDefinitions) {
        final HeaderBasedPlaceholderSubstitutionAlgorithm algorithm =
                HeaderBasedPlaceholderSubstitutionAlgorithm.newInstance(replacementDefinitions);
        final SubstitutionStrategyRegistry substitutionStrategyRegistry =
                ThingSubstitutionStrategyRegistry.newInstance();

        return new ThingsPlaceholderSubstitutionPreEnforcer(algorithm, substitutionStrategyRegistry);
    }
}
