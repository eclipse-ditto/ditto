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
package org.eclipse.ditto.model.base.common;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

/**
 * Unit test for {@link ConditionChecker}.
 */
public final class ConditionCheckerTest {


    @Test
    public void assertImmutability() {
        assertInstancesOf(ConditionChecker.class, areImmutable());
    }


    @Test(expected = IllegalArgumentException.class)
    public void checkArgumentWithPredicate() {
        final String argument = "";
        ConditionChecker.checkArgument(argument, s -> !s.isEmpty());
    }

}
