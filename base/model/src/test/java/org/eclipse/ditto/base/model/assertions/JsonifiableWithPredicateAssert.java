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
package org.eclipse.ditto.base.model.assertions;

import java.util.function.Predicate;

import org.assertj.core.api.AbstractAssert;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.Jsonifiable;


/**
 * Defines assertions for {@link org.eclipse.ditto.base.model.json.Jsonifiable.WithPredicate}.
 *
 * @param <J> the type of the JSON result
 * @param <P> the type which the predicate consumes for evaluation
 * @param <T> the type of the Jsonifiable
 * @param <A> used for navigational assertions to return the right assert type
 */
public interface JsonifiableWithPredicateAssert<J extends JsonValue, P, T extends Jsonifiable.WithPredicate<J, P>,
        A extends AbstractAssert<A, T>> {

    /**
     * Asserts that the fields matched by {@code predicate} are the same both for the
     * actual Jsonifiable and the {@code expected} Jsonifiable. Fields not matched by {@code predicate}
     * are not considered in the assertion.
     *
     * @param expected the expected Jsonifiable
     * @param predicate the predicate which is used to compare the things
     */
    A hasEqualJson(T expected, Predicate<P> predicate);
}
