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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.services.utils.distributedcache.model.BaseCacheEntry;
import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;
import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyCacheEntry}.
 */
public final class ModifyCacheEntryTest {

    private static final String ID = "WhereIsHubble?";
    private static final boolean ENSURE_WRITE_MAJORITY = true;
    private static final WriteConsistency WRITE_CONSISTENCY = WriteConsistency.MAJORITY;
    private static final long REVISION = 1953;
    private static final CacheEntry CACHE_ENTRY = BaseCacheEntry.newInstance(null, REVISION, false, null);
    
    private ModifyCacheEntry underTest;

    /** */
    @Before
    public void setUp() {
        underTest = new ModifyCacheEntry(ID, CACHE_ENTRY, WRITE_CONSISTENCY);
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyCacheEntry.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyCacheEntry.class, areImmutable(),
                provided(CacheEntry.class, WriteConsistency.class).areAlsoImmutable());
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToCreateInstanceWithNullId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new ModifyCacheEntry(null, CACHE_ENTRY, WRITE_CONSISTENCY))
                .withMessage("The %s must not be null!", "ID")
                .withNoCause();
    }

    /** */
    @Test
    public void tryToCreateInstanceWithEmptyId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ModifyCacheEntry("", CACHE_ENTRY, WRITE_CONSISTENCY))
                .withMessage("The argument 'ID' must not be empty!")
                .withNoCause();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToCreateInstanceWithNullCacheEntry() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new ModifyCacheEntry(ID, null, WRITE_CONSISTENCY))
                .withMessage("The %s must not be null!", "modified cache entry")
                .withNoCause();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToCreateInstanceWithNullWriteConsistency() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new ModifyCacheEntry(ID, CACHE_ENTRY, null))
                .withMessage("The %s must not be null!", "write consistency")
                .withNoCause();
    }

    /** */
    @Test
    public void getCacheEntryReturnsExpected() {
        assertThat(underTest.getCacheEntry()).isEqualTo(CACHE_ENTRY);
    }

    /** */
    @Test
    public void getIdReturnsExpected() {
        assertThat(underTest.getId()).isEqualTo(ID);
    }

    /** */
    @Test
    public void getWriteConsistencyReturnsExpected() {
        assertThat((Object) underTest.getWriteConsistency()).isEqualTo(WRITE_CONSISTENCY);
    }

}
