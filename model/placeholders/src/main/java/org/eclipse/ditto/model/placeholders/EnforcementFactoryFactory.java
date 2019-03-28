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
package org.eclipse.ditto.model.placeholders;

import org.eclipse.ditto.model.connectivity.Enforcement;

/**
 * Factory class that creates instances of {@link EnforcementFilterFactory}s.
 */
public final class EnforcementFactoryFactory {

    /**
     * Creates new instance of {@link EnforcementFilterFactory} which can be used to create new {@link
     * EnforcementFilter}s.
     *
     * @param enforcement the enforcement options containing the filter templates
     * @param inputFilter the input placeholder used to resolve the input value
     * @param filterPlaceholderResolver the filter placeholder used to resolve filter values
     * @param <I> the type from which the input values are resolved
     * @return the new {@link EnforcementFactoryFactory}
     */
    public static <I> EnforcementFilterFactory<I, String> newEnforcementFilterFactory(final Enforcement enforcement,
            final Placeholder<I> inputFilter, final Placeholder<String> filterPlaceholderResolver) {
        return new ImmutableEnforcementFilterFactory<>(enforcement, inputFilter, filterPlaceholderResolver);
    }

    /**
     * Creates new instance of {@link EnforcementFilterFactory} that is preconfigured with a {@link ThingPlaceholder}
     * for the filters.
     *
     * @param <O> the type from which the input values are resolved
     * @param enforcement the enforcement options
     * @param inputFilter the input filter that is applied to resolve input value
     * @return the new {@link EnforcementFactoryFactory} used to match the input
     */
    public static <O> EnforcementFilterFactory<O, String> newEnforcementFilterFactory(final Enforcement enforcement,
            final Placeholder<O> inputFilter) {
        return newEnforcementFilterFactory(enforcement, inputFilter, PlaceholderFactory.newThingPlaceholder());
    }

    private EnforcementFactoryFactory() {
        throw new AssertionError();
    }
}
