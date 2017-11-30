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
package org.eclipse.ditto.services.utils.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.utils.persistence.mongo.KeyNameReviser.DOLLAR_CHAR;
import static org.eclipse.ditto.services.utils.persistence.mongo.KeyNameReviser.DOLLAR_UNICODE_CHAR;
import static org.eclipse.ditto.services.utils.persistence.mongo.KeyNameReviser.DOT_CHAR;
import static org.eclipse.ditto.services.utils.persistence.mongo.KeyNameReviser.DOT_UNICODE_CHAR;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;
import java.util.function.Function;

import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.persistence.mongo.KeyNameReviser}.
 */
public final class KeyNameReviserTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(KeyNameReviser.class, areImmutable(), provided(Function.class).isAlsoImmutable());
    }

    @Test
    public void instanceReturnedForEscapingProblematicPlainCharsWorksAsExpected() {
        final String jsonKeyNamePattern = "org{0}eclipse{1}ditto{2}";
        final String originalJsonKeyName = MessageFormat.format(jsonKeyNamePattern, DOT_CHAR, DOT_CHAR, DOLLAR_CHAR);
        final String expectedRevisedJsonKeyName = MessageFormat.format(jsonKeyNamePattern, DOT_UNICODE_CHAR,
                DOT_UNICODE_CHAR, DOLLAR_UNICODE_CHAR);
        final KeyNameReviser underTest = KeyNameReviser.escapeProblematicPlainChars();

        final String revisedJsonKeyName = underTest.apply(originalJsonKeyName);

        assertThat(revisedJsonKeyName).isEqualTo(expectedRevisedJsonKeyName);
    }

    @Test
    public void instanceReturnedForDecodingKnownUnicodeCharsWorksAsExpected() {
        final String jsonKeyNamePattern = "org{0}eclipse{1}ditto{2}";
        final String originalJsonKeyName =
                MessageFormat.format(jsonKeyNamePattern, DOT_UNICODE_CHAR, DOT_UNICODE_CHAR, DOLLAR_UNICODE_CHAR);
        final String expectedRevisedJsonKeyName =
                MessageFormat.format(jsonKeyNamePattern, DOT_CHAR, DOT_CHAR, DOLLAR_CHAR);
        final KeyNameReviser underTest = KeyNameReviser.decodeKnownUnicodeChars();

        final String revisedJsonKeyName = underTest.apply(originalJsonKeyName);

        assertThat(revisedJsonKeyName).isEqualTo(expectedRevisedJsonKeyName);
    }

}