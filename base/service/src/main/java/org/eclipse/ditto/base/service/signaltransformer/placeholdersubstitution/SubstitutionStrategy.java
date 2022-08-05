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
package org.eclipse.ditto.base.service.signaltransformer.placeholdersubstitution;

import org.eclipse.ditto.base.model.signals.Signal;

/**
 * Defines the (placeholder) substitution strategy for a certain command (which is of type {@link Signal}.
 *
 * @param <T> the subtype of {@link Signal} handled by this strategy.
 */
public interface SubstitutionStrategy<T extends Signal<?>> {

    /**
     * Checks whether this strategy is applicable for the given {@code signal}.
     *
     * @param signal the command which may have content to be substituted.
     * @return {@code true}, if this strategy is applicable; {@code false}, otherwise.
     */
    boolean matches(Signal<?> signal);

    /**
     * Apply (placeholder) substitution on the given {@code signal} using the {@code substitutionAlgorithm}.
     *
     * @param signal the command which may have content to be substituted.
     * @param substitutionAlgorithm the algorithm to be used for placeholder substitution.
     * @return a copy of {@code signal} with substitutions applied, if substitutions were necessary; the
     * same {@code signal}, if no substitutions were necessary.
     */
    T apply(T signal, HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm);
}
