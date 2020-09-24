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
package org.eclipse.ditto.services.models.connectivity;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.EnforcementFilterFactory;
import org.eclipse.ditto.model.placeholders.Placeholder;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;

/**
 * Factory class that creates instances of {@link org.eclipse.ditto.model.connectivity.EnforcementFilterFactory}s.
 */
public final class EnforcementFactoryFactory {

    /**
     * Creates new instance of {@link org.eclipse.ditto.model.connectivity.EnforcementFilterFactory} which can be used to create new {@link
     * org.eclipse.ditto.model.connectivity.EnforcementFilter}s.
     *
     * @param enforcement the enforcement options containing the filter templates
     * @param inputFilter the input placeholder used to resolve the input value
     * @param filterPlaceholderResolver the filter placeholder used to resolve filter values
     * @param <I> the type from which the input values are resolved
     * @return the new {@link EnforcementFactoryFactory}
     */
    private static <I> EnforcementFilterFactory<I, CharSequence> newEnforcementFilterFactory(
            final Enforcement enforcement,
            final Placeholder<I> inputFilter, final List<Placeholder<CharSequence>> filterPlaceholderResolver) {
        return new ImmutableEnforcementFilterFactory<>(enforcement, inputFilter, filterPlaceholderResolver);
    }

    /**
     * Creates new instance of {@link EnforcementFilterFactory} that is preconfigured with a
     * {@link org.eclipse.ditto.model.placeholders.ThingPlaceholder} for the filters.
     *
     * @param <I> the type from which the input values are resolved
     * @param enforcement the enforcement options
     * @param inputFilter the input filter that is applied to resolve input value
     * @return the new {@link EnforcementFactoryFactory} used to match the input
     */
    public static <I> EnforcementFilterFactory<I, CharSequence> newEnforcementFilterFactory(
            final Enforcement enforcement,
            final Placeholder<I> inputFilter) {
        return newEnforcementFilterFactory(enforcement, inputFilter, Arrays.asList(
                PlaceholderFactory.newThingPlaceholder(),
                PlaceholderFactory.newPolicyPlaceholder(),
                PlaceholderFactory.newEntityPlaceholder()
        ));
    }

    private EnforcementFactoryFactory() {
        throw new AssertionError();
    }
}
