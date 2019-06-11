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
package org.eclipse.ditto.services.utils.persistence.mongo.suffixes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link NamespaceSuffixCollectionNames}.
 */
public final class NamespaceSuffixCollectionNamesTest {

    private NamespaceSuffixCollectionNames underTest;

    @Before
    public void setup() {
        NamespaceSuffixCollectionNames.resetConfig();
        underTest = new NamespaceSuffixCollectionNames();
    }

    @Test
    public void getSuffixFromPersistenceId() {
        NamespaceSuffixCollectionNames.setSupportedPrefixes(Collections.singleton("thing"));
        final String persistenceId = "thing:org.eclipse.ditto:test:thing";
        final String expectedSuffix = "org.eclipse.ditto";

        final String suffix = underTest.getSuffixFromPersistenceId(persistenceId);

        assertThat(suffix).isEqualTo(expectedSuffix);
    }

    @Test
    public void getSuffixFromPersistenceIdReturnsEmptySuffixIfPrefixIsNotSupported() {
        final String persistenceId = "some:org.eclipse.ditto:test:thing";
        final String expectedSuffix = "";

        NamespaceSuffixCollectionNames.setSupportedPrefixes(Collections.singletonList("thing"));
        final String suffix = underTest.getSuffixFromPersistenceId(persistenceId);

        assertThat(suffix).isEqualTo(expectedSuffix);
    }

    @Test
    public void getSuffixFromPersistenceIdWhenNoSupportedSuffixesSet() {
        final String persistenceId = "thing:org.eclipse.ditto:test:thing";

        assertThat(underTest.getSuffixFromPersistenceId(persistenceId)).isEmpty();
    }

    @Test
    public void validateMongoCharacters() {
        final String invalidInput = "This/should\\be.a s|tg\"with$so?me*has:hes<b";
        final String expected = invalidInput.replace('$', '#');

        NamespaceSuffixCollectionNames.setSupportedPrefixes(Collections.singletonList("thing"));
        final String sanitizedString = underTest.validateMongoCharacters(invalidInput);

        assertThat(sanitizedString).isEqualTo(expected);
    }

    @Test
    public void validateVeryLongNamespace() {
        final String invalidInput =
                "this.is.a.namespace.which.is.quite.long.so.that.not.all.characters.would.fit.as.collection.name";
        final String expected = invalidInput.substring(0, NamespaceSuffixCollectionNames.MAX_SUFFIX_CHARS_LENGTH) +
                "@" + Integer.toHexString(invalidInput.hashCode());

        NamespaceSuffixCollectionNames.setSupportedPrefixes(Collections.singletonList("thing"));
        final String sanitizedString = underTest.validateMongoCharacters(invalidInput);

        assertThat(sanitizedString).startsWith(invalidInput.substring(0, 20));
        assertThat(sanitizedString).isEqualTo(expected);
        assertThat(sanitizedString).doesNotEndWith(invalidInput);
    }

    @Test(expected = NamespaceSuffixCollectionNames.PersistenceIdInvalidException.class)
    public void getSuffixFromPersistenceIdThrowsIllegalStateException() {
        NamespaceSuffixCollectionNames.setSupportedPrefixes(Collections.singletonList("thing"));
        final String persistenceId = "thing:::::::";

        underTest.getSuffixFromPersistenceId(persistenceId);
    }

    @Test
    public void getSuffixFromPersistenceIdWithNamespaceAndOnlyColons() {
        NamespaceSuffixCollectionNames.setSupportedPrefixes(Collections.singletonList("thing"));
        final String persistenceId = "thing:org.eclipse.ditto:::::::";

        final String suffix = underTest.getSuffixFromPersistenceId(persistenceId);

        final String expectedSuffix = "org.eclipse.ditto";
        assertThat(suffix).isEqualTo(expectedSuffix);
    }

}
