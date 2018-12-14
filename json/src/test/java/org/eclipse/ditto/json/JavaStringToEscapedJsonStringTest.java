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
package org.eclipse.ditto.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.json.JavaStringToEscapedJsonString}.
 */
public final class JavaStringToEscapedJsonStringTest {
    
    private JavaStringToEscapedJsonString underTest;

    @Test
    public void assertImmutability() {
        assertInstancesOf(JavaStringToEscapedJsonString.class,
                areImmutable(),
                provided(Function.class).isAlsoImmutable());
    }

    @Before
    public void setUp() {
        underTest = JavaStringToEscapedJsonString.getInstance();
    }

    @Test
    public void convertJavaStringWithoutSpecialChars() {
        final String javaString = "Auf der Wiese blueht ein kleines Bluemelein.";
        final String expected = "\"" + javaString + "\"";

        final String actual = underTest.apply(javaString);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void convertJavaStringWithSpecialChars() {
        final String javaString = "Auf der Wiese\n blueht ein kleines \"Blümelein\".";
        final String expected = "\"" + "Auf der Wiese\\n blueht ein kleines \\\"Blümelein\\\"." + "\"";

        final String actual = underTest.apply(javaString);

        assertThat(actual).isEqualTo(expected);
    }

}