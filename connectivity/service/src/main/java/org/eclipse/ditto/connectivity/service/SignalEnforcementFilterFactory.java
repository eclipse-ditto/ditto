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
package org.eclipse.ditto.connectivity.service;

import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.Placeholder;
import org.eclipse.ditto.placeholders.PlaceholderFilter;

/**
 * Factory that creates instances of EnforcementFilterFactory.
 *
 */
@Immutable
final class SignalEnforcementFilterFactory implements EnforcementFilterFactory<ExpressionResolver, Signal<?>> {

    private static final List<Placeholder<EntityId>> FILTER_PLACEHOLDERS = List.of(
            ConnectivityPlaceholders.newThingPlaceholder(),
            ConnectivityPlaceholders.newPolicyPlaceholder(),
            ConnectivityPlaceholders.newEntityPlaceholder());

    private final Enforcement enforcement;

    /**
     * Instantiates a new {@link SignalEnforcementFilterFactory}.
     *
     * @param enforcement the enforcement configuration, contains the input and filters templates
     */
    SignalEnforcementFilterFactory(final Enforcement enforcement) {
        this.enforcement = enforcement;
    }

    /**
     * Creates a new {@link EnforcementFilter} for the enforcement's input template, resolved with the given
     * expression resolver.
     *
     * @param inputResolver resolves the placeholders in the enforcement input, e.g. {@code {{ header:<name> }}}
     * or {@code {{ source:address }}}.
     * @return a new {@link EnforcementFilter} which holds the resolved input value.
     * @throws org.eclipse.ditto.placeholders.UnresolvedPlaceholderException if a placeholder in the enforcement
     * input cannot be resolved.
     */
    @Override
    public EnforcementFilter<Signal<?>> getFilter(final ExpressionResolver inputResolver) {
        final String inputResolved = PlaceholderFilter.apply(enforcement.getInput(), inputResolver);
        return new SignalEnforcementFilter(enforcement, FILTER_PLACEHOLDERS, inputResolved);
    }

}
