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
package org.eclipse.ditto.services.utils.headers.conditional;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class ETagComparisonTest {

    private static final String WEAK_1 = "W/\"1\"";
    private static final String WEAK_2 = "W/\"2\"";
    private static final String STRONG_1 = "\"1\"";
    private static final String STRONG_2 = "\"2\"";

    @Test
    public void strongComparisonEvaluatesToFalseForEqualWeakTags() {
        assertThat(ETagComparison.strong(WEAK_1, WEAK_1)).isFalse();
    }

    @Test
    public void strongComparisonEvaluatesToFalseForDifferentWeakTags() {
        assertThat(ETagComparison.strong(WEAK_1, WEAK_2)).isFalse();
    }

    @Test
    public void strongComparisonEvaluatesToFalseForOneStrongTagWithSameValueThanWeakTag() {
        assertThat(ETagComparison.strong(WEAK_1, STRONG_1)).isFalse();
    }

    @Test
    public void strongComparisonEvaluatesToFalseForDifferentStrongTags() {
        assertThat(ETagComparison.strong(STRONG_1, STRONG_2)).isFalse();
    }

    @Test
    public void strongComparisonEvaluatesToTrueForEqualStrongTags() {
        assertThat(ETagComparison.strong(STRONG_1, STRONG_1)).isTrue();
    }

    @Test
    public void weakComparisonEvaluatesToTrueForEqualWeakTags() {
        assertThat(ETagComparison.weak(WEAK_1, WEAK_1)).isTrue();
    }

    @Test
    public void weakComparisonEvaluatesToFalseForDifferentWeakTags() {
        assertThat(ETagComparison.weak(WEAK_1, WEAK_2)).isFalse();
    }

    @Test
    public void weakComparisonEvaluatesToTrueForOneStrongTagWithSameValueThanWeakTag() {
        assertThat(ETagComparison.weak(WEAK_1, STRONG_1)).isTrue();
    }

    @Test
    public void weakComparisonEvaluatesToFalseForDifferentStrongTags() {
        assertThat(ETagComparison.weak(STRONG_1, STRONG_2)).isFalse();
    }

    @Test
    public void weakComparisonEvaluatesToTrueForEqualStrongTags() {
        assertThat(ETagComparison.weak(STRONG_1, STRONG_1)).isTrue();
    }
}