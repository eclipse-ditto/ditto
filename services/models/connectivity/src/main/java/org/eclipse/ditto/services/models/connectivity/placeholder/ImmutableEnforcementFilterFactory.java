/*
 *  Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 *  SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.models.connectivity.placeholder;

import org.eclipse.ditto.model.connectivity.Enforcement;

/**
 * Factory that creates instances of EnforcementFilterFactory.
 *
 * @param <I> the type required to resolve the placeholders in the input
 * @param <M> the type required to resolve the placeholders in the filters
 */
public class ImmutableEnforcementFilterFactory<I, M> implements EnforcementFilterFactory<I, M> {

    private final Enforcement enforcement;
    private final Placeholder<I> inputPlaceholder;
    private final Placeholder<M> filterPlaceholder;

    /**
     * Instantiates a new {@link ImmutableEnforcementFilterFactory}.
     *
     * @param enforcement the enforcement configuration, contains the input and filters templates
     * @param inputPlaceholder the input placeholder used to resolve the values in the input template
     * @param filterPlaceholder the filters placeholder used to resolve the values in the filters template
     */
    ImmutableEnforcementFilterFactory(final Enforcement enforcement,
            final Placeholder<I> inputPlaceholder,
            final Placeholder<M> filterPlaceholder) {
        this.enforcement = enforcement;
        this.inputPlaceholder = inputPlaceholder;
        this.filterPlaceholder = filterPlaceholder;
    }

    @Override
    public EnforcementFilter<M> getFilter(final I input) {
        final String inputResolved = PlaceholderFilter.apply(enforcement.getInput(), input, inputPlaceholder);
        return new ImmutableEnforcementFilter<>(enforcement, filterPlaceholder, inputResolved);
    }

}
