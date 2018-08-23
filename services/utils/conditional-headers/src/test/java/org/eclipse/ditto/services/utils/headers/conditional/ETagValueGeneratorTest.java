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
package org.eclipse.ditto.services.utils.headers.conditional;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.Entity;
import org.eclipse.ditto.model.base.entity.Revision;
import org.junit.Test;

public class ETagValueGeneratorTest {

    @Test
    public void generateForNonDeletedEntity() {
        final String mockedETagValue = "4711";
        final Entity mockEntity = createMockEntity(false, mockedETagValue);

        assertETagGeneration(mockEntity, mockedETagValue);
    }

    @Test
    public void generateForDeletedEntity() {
        final Entity mockEntity = createMockEntity(true, "4711");

        assertETagGeneration(mockEntity, null);
    }

    @Test
    public void generateForNonEntityObject() {
        final  String arbitraryObject = "1234";
        final String expectedETagValue = String.valueOf(arbitraryObject.hashCode());

        assertETagGeneration(arbitraryObject, expectedETagValue);
    }

    @Test
    public void generateForNull() {
        final Optional<CharSequence> generatedETagValue = ETagValueGenerator.generate(null);

        assertThat(generatedETagValue).isNotPresent();
    }

    private static Entity createMockEntity(final boolean deleted, final String mockedETagValue) {
        final Revision mockRevision = mock(Revision.class);
        when(mockRevision.toString()).thenReturn(mockedETagValue);
        final Entity mockEntity = mock(Entity.class);
        when(mockEntity.isDeleted()).thenReturn(deleted);
        when(mockEntity.getRevision()).thenReturn(Optional.of(mockRevision));
        return mockEntity;
    }

    private void assertETagGeneration(final Object obj, @Nullable final String expectedETagValue) {
        final Optional<CharSequence> generatedETagValueOpt = ETagValueGenerator.generate(obj);
        if (expectedETagValue == null) {
            assertThat(generatedETagValueOpt).isNotPresent();
            return;
        }

        final CharSequence generatedETagValue = generatedETagValueOpt.orElseThrow(() -> new
                AssertionError("Expected ETag, but is empty."));

        assertThat(generatedETagValue).isEqualTo(expectedETagValue);
    }
}