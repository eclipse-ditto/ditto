/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.HeaderBasedPlaceholderSubstitutionAlgorithm;

/**
 * Defines the (placeholder) substitution strategy for a certain command (which is of type {@link WithDittoHeaders}.
 *
 * @param <T> the subtype of {@link WithDittoHeaders} handled by this strategy.
 */
public interface SubstitutionStrategy<T extends WithDittoHeaders> {

    /**
     * Checks whether this strategy is applicable for the given {@code withDittoHeaders}.
     *
     * @param withDittoHeaders the command which may have content to be substituted.
     * @return {@code true}, if this strategy is applicable; {@code false}, otherwise.
     */
    boolean matches(final WithDittoHeaders withDittoHeaders);

    /**
     * Apply (placeholder) substitution on the given {@code withDittoHeaders} using the {@code substitutionAlgorithm}.
     *
     * @param withDittoHeaders the command which may have content to be substituted.
     * @param substitutionAlgorithm the algorithm to be used for placeholder substitution.
     * @return a copy of {@code withDittoHeaders} with substitutions applied, if substitutions were necessary; the
     * same {@code withDittoHeaders}, if no substitutions were necessary.
     */
    WithDittoHeaders apply(final T withDittoHeaders,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm);
}
