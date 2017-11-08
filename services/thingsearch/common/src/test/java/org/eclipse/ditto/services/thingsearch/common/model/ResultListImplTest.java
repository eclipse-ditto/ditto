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
package org.eclipse.ditto.services.thingsearch.common.model;

import java.util.Arrays;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link ResultListImpl}.
 */
public final class ResultListImplTest {

    /** */
    @Test
    public void hashcodeAndEquals() {
        EqualsVerifier.forClass(ResultListImpl.class).usingGetClass().verify();
    }

    // We cannot guarantee immutability for ResultListImpl, because its elements might not be immutable
    // /** */
    // @Test
    // public void immutability()
    // {
    // assertInstancesOf(ResultListImpl.class, areImmutable());
    // }

    /** */
    @Test(expected = NullPointerException.class)
    public void constructorWithNull() {
        new ResultListImpl<>(null, 1);
    }

    /** */
    @Test
    public void basicUsage() {
        final int nextPageOffset = 4;
        final String first = "a";
        final String second = "b";

        final ResultListImpl<String> resultList = new ResultListImpl<>(Arrays.asList(first, second), nextPageOffset);
        Assertions.assertThat(resultList).isNotEmpty().containsOnly(first, second).hasSize(2);
        Assertions.assertThat(resultList.get(0)).isEqualTo(first);
        Assertions.assertThat(resultList.get(1)).isEqualTo(second);
        Assertions.assertThat(resultList.nextPageOffset()).isEqualTo(nextPageOffset);
    }
}
