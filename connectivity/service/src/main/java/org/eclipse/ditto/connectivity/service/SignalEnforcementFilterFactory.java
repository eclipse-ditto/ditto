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
import org.eclipse.ditto.placeholders.Placeholder;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.PlaceholderFilter;

/**
 * Factory that creates instances of EnforcementFilterFactory.
 *
 * @param <I> the type required to resolve the placeholders in the input
 */
@Immutable
final class SignalEnforcementFilterFactory<I> implements EnforcementFilterFactory<I, Signal<?>> {

    private final Enforcement enforcement;
    private final Placeholder<I> inputPlaceholder;
    private final List<Placeholder<EntityId>> filterPlaceholder;

    /**
     * Instantiates a new {@link SignalEnforcementFilterFactory}.
     *
     * @param enforcement the enforcement configuration, contains the input and filters templates
     * @param inputPlaceholder the input placeholder used to resolve the values in the input template
     * @param filterPlaceholder the filters placeholder used to resolve the values in the filters template
     */
    SignalEnforcementFilterFactory(final Enforcement enforcement,
            final Placeholder<I> inputPlaceholder,
            final List<Placeholder<EntityId>> filterPlaceholder) {
        this.enforcement = enforcement;
        this.inputPlaceholder = inputPlaceholder;
        this.filterPlaceholder = filterPlaceholder;
    }

    @Override
    public EnforcementFilter<Signal<?>> getFilter(final I input) {
        final String inputResolved = PlaceholderFilter.apply(enforcement.getInput(),
                PlaceholderFactory.newExpressionResolver(inputPlaceholder, input));
        return new SignalEnforcementFilter(enforcement, filterPlaceholder, inputResolved);
    }

}
