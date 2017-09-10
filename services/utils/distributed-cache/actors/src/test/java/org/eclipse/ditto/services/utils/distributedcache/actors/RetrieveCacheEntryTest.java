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
package org.eclipse.ditto.services.utils.distributedcache.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveCacheEntry}.
 */
public final class RetrieveCacheEntryTest {

    private static final String ID = "AlfredWegener";
    private static final Object CONTEXT = new Object();
    private static final ReadConsistency READ_CONSISTENCY = ReadConsistency.ALL;
    
    private RetrieveCacheEntry underTest;

    /** */
    @Before
    public void setUp() {
        underTest = new RetrieveCacheEntry(ID, CONTEXT, READ_CONSISTENCY);
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveCacheEntry.class)
                .usingGetClass()
                .verify();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToCreateInstanceWithNullId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new RetrieveCacheEntry(null, CONTEXT, READ_CONSISTENCY))
                .withMessage("The %s must not be null!", "ID")
                .withNoCause();
    }

    /** */
    @Test
    public void tryToCreateInstanceWithEmptyId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new RetrieveCacheEntry("", CONTEXT, READ_CONSISTENCY))
                .withMessage("The argument 'ID' must not be empty!")
                .withNoCause();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToCreateInstanceWithNullReadConsistency() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new RetrieveCacheEntry(ID, CONTEXT, null))
                .withMessage("The %s must not be null!", "read consistency")
                .withNoCause();
    }

    /** */
    @Test
    public void contextMayBeNull() {
        underTest = new RetrieveCacheEntry(ID, null, READ_CONSISTENCY);

        assertThat(underTest).isNotNull();
    }

    /** */
    @Test
    public void getReadConsistencyReturnsExpected() {
        assertThat((Object) underTest.getReadConsistency()).isEqualTo(READ_CONSISTENCY);
    }

    /** */
    @Test
    public void getIdReturnsExpected() {
        assertThat(underTest.getId()).isEqualTo(ID);
    }

    /** */
    @Test
    public void getContextReturnsExpected() {
        assertThat(underTest.getContext()).contains(CONTEXT);
    }

}
