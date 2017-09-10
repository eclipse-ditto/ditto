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

import org.eclipse.ditto.services.utils.distributedcache.model.BaseCacheEntry;
import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;
import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveCacheEntryResponse}.
 */
public final class RetrieveCacheEntryResponseTest {

    private static final String ID = "IsaacNewton";
    private static final long REVISION = 1687;
    private static final CacheEntry CACHE_ENTRY = BaseCacheEntry.newInstance(null, REVISION, false, null);
    private static final Object CONTEXT = new Object();
    
    private RetrieveCacheEntryResponse underTest;

    /** */
    @Before
    public void setUp() {
        underTest = new RetrieveCacheEntryResponse(ID, CACHE_ENTRY, CONTEXT);
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveCacheEntryResponse.class)
                .usingGetClass()
                .verify();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToCreateInstanceWithNullId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new RetrieveCacheEntryResponse(null, CACHE_ENTRY, CONTEXT))
                .withMessage("The %s must not be null!", "ID")
                .withNoCause();
    }

    /** */
    @Test
    public void tryToCreateInstanceWithEmptyId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new RetrieveCacheEntryResponse("", CACHE_ENTRY, CONTEXT))
                .withMessage("The argument 'ID' must not be empty!")
                .withNoCause();
    }

    /** */
    @Test
    public void getCacheEntryReturnsExpected() {
        assertThat(underTest.getCacheEntry()).contains(CACHE_ENTRY);
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
