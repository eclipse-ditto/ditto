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

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.Placeholder;
import org.eclipse.ditto.placeholders.PlaceholderFactory;

/**
 * Factory class that creates instances of {@link EnforcementFilterFactory}s.
 */
public final class EnforcementFactoryFactory {

    /**
     * Creates a new {@link EnforcementFilterFactory} which resolves the enforcement input with a given
     * {@link ExpressionResolver}. The filters can use the thing, policy and entity placeholders.
     *
     * @param enforcement the enforcement containing the input and filter templates.
     * @return the new enforcement filter factory.
     */
    public static EnforcementFilterFactory<ExpressionResolver, Signal<?>> newEnforcementFilterFactory(
            final Enforcement enforcement) {
        return new SignalEnforcementFilterFactory(enforcement);
    }

    /**
     * Creates new instance of {@link EnforcementFilterFactory}.
     *
     * @param <I> the type from which the input values are resolved
     * @param enforcement the enforcement options
     * @return the new {@link EnforcementFactoryFactory} used to match the input
     */
    public static <I> EnforcementFilterFactory<I, Signal<?>> newEnforcementFilterFactory(
            final Enforcement enforcement, final Placeholder<I> inputPlaceholder) {
        final EnforcementFilterFactory<ExpressionResolver, Signal<?>> factory =
                newEnforcementFilterFactory(enforcement);
        return input -> factory.getFilter(PlaceholderFactory.newExpressionResolver(inputPlaceholder, input));
    }

    private EnforcementFactoryFactory() {
        throw new AssertionError();
    }

}
