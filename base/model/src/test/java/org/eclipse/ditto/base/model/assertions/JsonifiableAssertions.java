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

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.Jsonifiable;


/**
 * Assertions for {@link org.eclipse.ditto.base.model.json.Jsonifiable}s which are only valid for certain subtypes such as {@link
 * org.eclipse.ditto.base.model.json.Jsonifiable.WithFieldSelector} or {@link org.eclipse.ditto.base.model.json.Jsonifiable.WithPredicate}.
 */
public final class JsonifiableAssertions {

    private JsonifiableAssertions() {
        throw new AssertionError();
    }

    /**
     * Asserts that the fields matched by {@code fieldSelector} and {@code predicate} are the same both for the
     * {@code actual} Jsonifiable and the {@code expected} Jsonifiable. Fields not matched by {@code fieldSelector}
     * and {@code predicate} are not considered in the assertion.
     *
     * @param actual the actual Jsonifiable
     * @param expected the expected Jsonifiable
     * @param fieldSelector the field selector which is used to compare the Jsonifiables
     * @param predicate the predicate which is used to compare the things
     * @param <P> the type which the predicate consumes for evaluation
     * @param <T> the type of the Jsonifiable
     */
    public static <P, T extends Jsonifiable.WithFieldSelectorAndPredicate<P>> void hasEqualJson(final T actual,
            final T expected, final JsonFieldSelector fieldSelector, final Predicate<P> predicate) {
        requireNonNull(fieldSelector);
        requireNonNull(predicate);

        assertThat(actual).isNotNull();
        assertThat(expected).isNotNull();

        final JsonObject actualJson = actual.toJson(fieldSelector, predicate);
        final JsonObject expectedJson = expected.toJson(fieldSelector, predicate);

        assertThat(actualJson).isEqualTo(expectedJson);
    }

    /**
     * Asserts that the fields matched by {@code predicate} are the same both for the
     * {@code actual} Jsonifiable and the {@code expected} Jsonifiable. Fields not matched by {@code predicate}
     * are not considered in the assertion.
     *
     * @param actual the actual Jsonifiable
     * @param expected the expected Jsonifiable
     * @param predicate the predicate which is used to compare the things
     * @param <J> the type of the JSON result
     * @param <P> the type which the predicate consumes for evaluation
     * @param <T> the type of the Jsonifiable
     */
    public static <J extends JsonValue, P, T extends Jsonifiable.WithPredicate<J, P>> void hasEqualJson(final T actual,
            final T expected, final Predicate<P> predicate) {
        requireNonNull(predicate);

        assertThat(actual).isNotNull();
        assertThat(expected).isNotNull();

        final J actualJson = actual.toJson(predicate);
        final J expectedJson = expected.toJson(predicate);

        assertThat(actualJson).isEqualTo(expectedJson);
    }
}
