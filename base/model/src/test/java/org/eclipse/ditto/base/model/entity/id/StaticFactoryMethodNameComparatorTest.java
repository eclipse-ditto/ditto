/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.entity.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.base.model.entity.id.EntityIdStaticFactoryMethodResolver.StaticFactoryMethodNameComparator.PREFERRED_METHOD_NAMES_ASCENDING;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.entity.id.EntityIdStaticFactoryMethodResolver.StaticFactoryMethodNameComparator;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link StaticFactoryMethodNameComparator}.
 */
@RunWith(Enclosed.class)
public final class StaticFactoryMethodNameComparatorTest {

    public static final class GeneralFunctionalityTest {

        @Test
        public void sortNamesWithComparatorUnderTestReturnsExpected() {
            final Stream<String> inputNames = Stream.<String>builder()
                    .add("c")
                    .add(PREFERRED_METHOD_NAMES_ASCENDING.get(1))
                    .add("x")
                    .add(PREFERRED_METHOD_NAMES_ASCENDING.get(2))
                    .add("a")
                    .add(PREFERRED_METHOD_NAMES_ASCENDING.get(0))
                    .add("m")
                    .build();
            final StaticFactoryMethodNameComparator underTest = new StaticFactoryMethodNameComparator();

            final List<String> sorted = inputNames.sorted(underTest).collect(Collectors.toList());

            assertThat(sorted).containsExactly(PREFERRED_METHOD_NAMES_ASCENDING.get(0),
                    PREFERRED_METHOD_NAMES_ASCENDING.get(1),
                    PREFERRED_METHOD_NAMES_ASCENDING.get(2),
                    "a",
                    "c",
                    "m",
                    "x");
        }

    }

    @RunWith(Parameterized.class)
    public static final class ParameterizedTest {

        @Parameterized.Parameter(0)
        public String methodNameBlue;

        @Parameterized.Parameter(1)
        public String methodNameGreen;

        @Parameterized.Parameter(2)
        public int expectedComparisonResult;

        @Parameterized.Parameters(name = "''{0}'' compared to ''{1}'' yields {2}")
        public static Collection<Object[]> parameters() {
            return Arrays.asList(new Object[][]{
                    {"a", "a", 0},
                    {"a", PREFERRED_METHOD_NAMES_ASCENDING.get(0), 1},
                    {PREFERRED_METHOD_NAMES_ASCENDING.get(0), "a", -1},
                    {PREFERRED_METHOD_NAMES_ASCENDING.get(0), PREFERRED_METHOD_NAMES_ASCENDING.get(0), 0},
                    {PREFERRED_METHOD_NAMES_ASCENDING.get(0), PREFERRED_METHOD_NAMES_ASCENDING.get(1), -1},
                    {PREFERRED_METHOD_NAMES_ASCENDING.get(1), PREFERRED_METHOD_NAMES_ASCENDING.get(0), 1},
                    {"a", "b", -1}
            });
        }

        @Test
        public void compareMethodNames() {
            final StaticFactoryMethodNameComparator underTest = new StaticFactoryMethodNameComparator();

            assertThat(underTest.compare(methodNameBlue, methodNameGreen)).isEqualTo(expectedComparisonResult);
        }

    }

}
