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
package org.eclipse.ditto.base.model.headers.entitytag;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.Entity;
import org.eclipse.ditto.base.model.entity.Revision;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTagBuilder}.
 */
public class EntityTagBuilderTest {

    @Test
    public void buildForNonDeletedEntity() {
        final String mockedRevision = "4711";
        final Entity mockEntity = createMockEntity(false, mockedRevision);

        final String expectedETagValue = "\"rev:4711\"";
        assertEntityTagGeneration(mockEntity, EntityTag.fromString(expectedETagValue));
    }

    @Test
    public void buildForDeletedEntity() {
        final Entity mockEntity = createMockEntity(true, "4711");

        assertEntityTagGeneration(mockEntity, null);
    }

    @Test
    public void buildForNonEntityObject() {
        final String arbitraryObject = "1234";
        final String expectedETagValue = "\"hash:" + Integer.toHexString(arbitraryObject.hashCode()) + "\"";

        assertEntityTagGeneration(arbitraryObject, EntityTag.fromString(expectedETagValue));
    }

    @Test
    public void buildForNull() {
        final Optional<EntityTag> builtETagValue = EntityTagBuilder.buildFromEntity(null);

        assertThat(builtETagValue).isNotPresent();
    }

    private static Entity createMockEntity(final boolean deleted, final String mockedETagValue) {
        final Revision mockRevision = mock(Revision.class);
        when(mockRevision.toString()).thenReturn(mockedETagValue);
        final Entity mockEntity = mock(Entity.class);
        when(mockEntity.isDeleted()).thenReturn(deleted);
        when(mockEntity.getRevision()).thenReturn(Optional.of(mockRevision));
        return mockEntity;
    }

    private void assertEntityTagGeneration(final Object obj, @Nullable final EntityTag expectedEntityTag) {
        final Optional<EntityTag> builtEntityTagOpt = EntityTagBuilder.buildFromEntity(obj);
        if (expectedEntityTag == null) {
            assertThat(builtEntityTagOpt).isNotPresent();
            return;
        }

        final EntityTag builtEntityTag = builtEntityTagOpt.orElseThrow(() -> new
                AssertionError("Expected ETag, but is empty."));

        assertThat(builtEntityTag).isEqualTo(expectedEntityTag);
    }
}
