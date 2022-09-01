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
package org.eclipse.ditto.policies.model.enforcers.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.policies.model.enforcers.tree.PointerLocationEvaluator}.
 */
public final class PointerLocationEvaluatorTest {

    private static final JsonPointer REFERENCE_POINTER = JsonFactory.newPointer("foo/bar/baz/boing");

    private PointerLocationEvaluator underTest;

    @Before
    public void setUp() {
        underTest = new PointerLocationEvaluator(REFERENCE_POINTER);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(PointerLocationEvaluator.class,
                areImmutable(),
                provided(JsonPointer.class).isAlsoImmutable());
    }

    @Test
    public void evaluationPointerIsDifferent() {
        final JsonPointer evaluationPointer = JsonFactory.newPointer("john/titor");

        final PointerLocation pointerLocation = underTest.apply(evaluationPointer);

        assertThat(pointerLocation).isSameAs(PointerLocation.DIFFERENT);
    }

    @Test
    public void evaluationPointerIsAbove() {
        final JsonPointer evaluationPointer = JsonFactory.newPointer("foo/bar/baz");

        final PointerLocation pointerLocation = underTest.apply(evaluationPointer);

        assertThat(pointerLocation).isSameAs(PointerLocation.ABOVE);
    }

    @Test
    public void evaluationPointerIsSame() {
        final JsonPointer evaluationPointer = JsonFactory.newPointer(REFERENCE_POINTER.toString());

        final PointerLocation pointerLocation = underTest.apply(evaluationPointer);

        assertThat(pointerLocation).isSameAs(PointerLocation.SAME);
    }

    @Test
    public void evaluationPointerIsBelow() {
        final JsonPointer evaluationPointer = REFERENCE_POINTER.addLeaf(JsonFactory.newKey("peng"));

        final PointerLocation pointerLocation = underTest.apply(evaluationPointer);

        assertThat(pointerLocation).isSameAs(PointerLocation.BELOW);
    }

}
