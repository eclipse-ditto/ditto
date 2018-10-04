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
 * @param <O> the input type for the input filter
 * @param <M> the input type for the matcher filter
 */
public class ImmutableEnforcementFilterFactory<O, M> implements EnforcementFilterFactory<O, M> {

    private final Enforcement enforcement;
    private final Placeholder<O> inputFilter;
    private final Placeholder<M> matcherFilter;

    /**
     * Instantiates a new {@link ImmutableEnforcementFilterFactory}.
     *
     * @param enforcement the enforcement configuration, contains the input and matcher templates
     * @param inputFilter the input filter that should be applied to resolve the values in the template
     * @param matcherFilter the matcher filter that should be applied to resolve the values in the template
     */
    ImmutableEnforcementFilterFactory(final Enforcement enforcement,
            final Placeholder<O> inputFilter,
            final Placeholder<M> matcherFilter) {
        this.enforcement = enforcement;
        this.inputFilter = inputFilter;
        this.matcherFilter = matcherFilter;
    }

    @Override
    public EnforcementFilter<M> getFilter(final O input) {
        final String inputResolved = PlaceholderFilter.apply(enforcement.getInput(), input, inputFilter);
        return new ImmutableEnforcementFilter<>(enforcement, matcherFilter, inputResolved);
    }

}
