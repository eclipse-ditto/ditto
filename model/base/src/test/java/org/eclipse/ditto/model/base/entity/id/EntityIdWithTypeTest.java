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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.junit.Test;

/**
 * Unit test for {@link EntityIdWithType}.
 * It uses a dummy implementation as this class only tests commonly implemented methods of EntityIdWithType.
 */
public final class EntityIdWithTypeTest {

    private static final EntityType ENTITY_TYPE_PLUMBUS = EntityType.of("plumbus");

    @Test
    public void checkSameId() {
        final EntityIdWithType entityIdFoo = DummyImplementation.of(ENTITY_TYPE_PLUMBUS, "foo");

        assertThat(entityIdFoo.isCompatibleOrThrow(entityIdFoo)).isTrue();
    }

    @Test
    public void checkDifferentIds() {
        final EntityIdWithType entityIdFoo = DummyImplementation.of(ENTITY_TYPE_PLUMBUS, "foo");
        final EntityIdWithType entityIdBar = DummyImplementation.of(ENTITY_TYPE_PLUMBUS, "bar");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> entityIdFoo.isCompatibleOrThrow(entityIdBar))
                .withMessage("The entity ID <%s> is not compatible with <%s>!", entityIdBar, entityIdFoo)
                .withNoCause();
    }

    private static final class DummyImplementation extends EntityIdWithType {

        private final EntityType entityType;

        private DummyImplementation(final EntityType entityType, final EntityId entityId) {
            super(entityId);
            this.entityType = entityType;
        }

        static DummyImplementation of(final EntityType entityType, final CharSequence entityId) {
            return new DummyImplementation(entityType, DefaultEntityId.of(entityId));
        }

        @Override
        public EntityType getEntityType() {
            return entityType;
        }

    }

}
