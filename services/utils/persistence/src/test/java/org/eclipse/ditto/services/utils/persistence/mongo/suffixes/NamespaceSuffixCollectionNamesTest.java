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
package org.eclipse.ditto.services.utils.persistence.mongo.suffixes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

public class NamespaceSuffixCollectionNamesTest {

    private NamespaceSuffixCollectionNames sut;

    @Before
    public void setup() {
        NamespaceSuffixCollectionNames.resetConfig();
        this.sut = new NamespaceSuffixCollectionNames();
    }

    @Test
    public void getSuffixFromPersistenceId() {
        NamespaceSuffixCollectionNames.setConfig(new SuffixBuilderConfig(Collections.singletonList("thing")));
        final String persistenceId = "thing:org.eclipse.ditto:test:thing";

        final String suffix = sut.getSuffixFromPersistenceId(persistenceId);

        final String expectedSuffix = "org%eclipse%ditto";
        assertThat(suffix).isEqualTo(expectedSuffix);
    }

    @Test
    public void getSuffixFromPersistenceIdReturnsEmptySuffixIfPrefixIsNotSupported() {
        NamespaceSuffixCollectionNames.setConfig(new SuffixBuilderConfig(Collections.singletonList("thing")));
        final String persistenceId = "some:org.eclipse.ditto:test:thing";

        final String suffix = sut.getSuffixFromPersistenceId(persistenceId);

        final String expectedSuffix = "";
        assertThat(suffix).isEqualTo(expectedSuffix);
    }

    @Test(expected = NullPointerException.class)
    public void getSuffixFromPersistenceIdThrowsNullpointerExceptionWithoutConfig() {
        final String persistenceId = "thing:org.eclipse.ditto:test:thing";

        sut.getSuffixFromPersistenceId(persistenceId);
    }

    @Test
    public void validateMongoCharacters() {
        NamespaceSuffixCollectionNames.setConfig(new SuffixBuilderConfig(Collections.singletonList("thing")));
        final String invalidInput =
                "This/should\\be.a string\"separated$by*hashes<and>not:by|strange?characters";

        final String sanitizedString = sut.validateMongoCharacters(invalidInput);

        final String expected = "This#should#be%a#string#separated#by#hashes#and#not#by#strange#characters";
        assertThat(sanitizedString).isEqualTo(expected);
    }

    @Test(expected = NullPointerException.class)
    public void validateMongoCharactersThrowsNullpointerExceptionWithoutConfig() {
        final String persistenceId = "thing:org.eclipse.ditto:test:thing";

        sut.getSuffixFromPersistenceId(persistenceId);
    }
}