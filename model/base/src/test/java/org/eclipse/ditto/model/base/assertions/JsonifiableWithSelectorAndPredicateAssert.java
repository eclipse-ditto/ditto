/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.base.assertions;

import java.util.function.Predicate;

import org.assertj.core.api.AbstractAssert;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Defines assertions for {@link Jsonifiable.WithPredicate}.
 *
 * @param <P> the type which the predicate consumes for evaluation
 * @param <T> the type of the Jsonifiable
 * @param <A> used for navigational assertions to return the right assert type
 */
public interface JsonifiableWithSelectorAndPredicateAssert<P, T extends Jsonifiable.WithFieldSelectorAndPredicate<P>,
        A extends AbstractAssert<A, T>> {

    /**
     * Asserts that the fields matched by {@code fieldSelector} and {@code predicate} are the same both for the
     * actual Jsonifiable and the {@code expected} Jsonifiable. Fields not matched by {@code fieldSelector}
     * and {@code predicate} are not considered in the assertion.
     *
     * @param expected the expected Jsonifiable
     * @param fieldSelector the field selector which is used to compare the Jsonifiables
     * @param predicate the predicate which is used to compare the things
     */
    A hasEqualJson(T expected, JsonFieldSelector fieldSelector, Predicate<P> predicate);
}
