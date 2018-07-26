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

import org.junit.Before;
import org.junit.Test;

public class NamespaceSuffixCollectionNamesTest {

    private NamespaceSuffixCollectionNames sut;

    @Before
    public void setup() {
        this.sut = new NamespaceSuffixCollectionNames();
    }

    @Test
    public void getSuffixFromPersistenceId() {
        final String persistenceId = "thing:org.eclipse.ditto:test:thing";

        final String suffix = sut.getSuffixFromPersistenceId(persistenceId);

        final String expectedSuffix = "org%eclipse%ditto";
        assertThat(suffix).isEqualTo(expectedSuffix);
    }

    @Test
    public void validateMongoCharacters() {
        final String invalidInput =
                "This/should\\be.a string\"separated$by*hashes<and>not:by|strange?characters";

        final String sanitizedString = sut.validateMongoCharacters(invalidInput);

        final String expected = "This#should#be%a#string#separated#by#hashes#and#not#by#strange#characters";
        assertThat(sanitizedString).isEqualTo(expected);
    }
}