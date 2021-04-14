/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.base.entity.id;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.junit.Test;

/**
 * Unit test for {@link EntityId}.
 * It uses a dummy implementation as this class only tests commonly implemented methods of EntityId.
 */
public final class EntityIdTest {

    private static final EntityType ENTITY_TYPE_PLUMBUS = EntityType.of("plumbus");

    @Test
    public void checkSameId() {
        final EntityId entityIdFoo = FallbackEntityId.of(ENTITY_TYPE_PLUMBUS, "foo");

        assertThat(entityIdFoo.isCompatible(entityIdFoo)).isTrue();
    }

    @Test
    public void checkDifferentIds() {
        final EntityId entityIdFoo = FallbackEntityId.of(ENTITY_TYPE_PLUMBUS, "foo");
        final EntityId entityIdBar = FallbackEntityId.of(ENTITY_TYPE_PLUMBUS, "bar");

        assertThat(entityIdFoo.isCompatible(entityIdBar)).isFalse();
    }

}
