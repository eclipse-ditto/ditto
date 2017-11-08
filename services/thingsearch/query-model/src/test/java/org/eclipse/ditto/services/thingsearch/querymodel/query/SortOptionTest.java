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
package org.eclipse.ditto.services.thingsearch.querymodel.query;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.SortFieldExpression;
import org.junit.Test;
import org.mockito.Mockito;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link SortOption}.
 */
public final class SortOptionTest {

    /** */
    @Test
    public void hashcodeAndEquals() {
        EqualsVerifier.forClass(SortOption.class).usingGetClass().verify();
    }

    /** */
    @Test
    public void immutability() {
        MutabilityAssert.assertInstancesOf(SortOption.class, MutabilityMatchers.areImmutable(),
                AllowedReason.provided(SortFieldExpression.class).isAlsoImmutable());
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void constructorWithNullExpression() {
        new SortOption(null, SortDirection.ASC);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void constructorWithNullDirection() {
        new SortOption(Mockito.mock(SortFieldExpression.class), null);
    }

    /** */
    @Test
    public void basicUsage() {
        final SortFieldExpression sortExpression = Mockito.mock(SortFieldExpression.class);
        final SortDirection sortDirection = SortDirection.ASC;

        final SortOption sortOption = new SortOption(sortExpression, sortDirection);

        Assertions.assertThat(sortOption.getSortExpression()).isEqualTo(sortExpression);
        Assertions.assertThat(sortOption.getSortDirection()).isEqualTo(sortDirection);
    }
}
