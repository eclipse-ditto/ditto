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
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.services.utils.distributedcache.model.BaseCacheEntry;
import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;
import org.junit.Before;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DeleteCacheEntry}.
 */
public final class DeleteCacheEntryTest {

    private static final String ID = "EdwardDrinkerCope";
    private static final long REVISION = 1337;
    private static final WriteConsistency WRITE_CONSISTENCY = WriteConsistency.MAJORITY;
    private static final CacheEntry LIVE_CACHE_ENTRY = BaseCacheEntry.newInstance(null, REVISION, false, null);

    private DeleteCacheEntry underTest;

    /** */
    @Before
    public void setUp() {
        underTest = new DeleteCacheEntry(ID, LIVE_CACHE_ENTRY, REVISION, WRITE_CONSISTENCY);
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DeleteCacheEntry.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(DeleteCacheEntry.class,
                areImmutable(),
                provided(CacheEntry.class, WriteConsistency.class).areAlsoImmutable());
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToCreateNewInstanceWithNullId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new DeleteCacheEntry(null, LIVE_CACHE_ENTRY, REVISION, WRITE_CONSISTENCY))
                .withMessage("The %s must not be null!", "ID to delete from the cache")
                .withNoCause();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToCreateInstanceWithNullWriteConsistency() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new DeleteCacheEntry(ID, LIVE_CACHE_ENTRY, REVISION, null))
                .withMessage("The %s must not be null!", "write consistency")
                .withNoCause();
    }

    /** */
    @Test
    public void getRevisionNumberReturnsExpected() {
        assertThat(underTest.getRevisionNumber()).isEqualTo(REVISION);
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
