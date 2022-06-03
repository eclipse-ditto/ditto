/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.entity.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.junit.Test;

/**
 * Unit test for {@link BaseEntityIdFactory}.
 */
public final class BaseEntityIdFactoryTest {

    private static final EntityType ENTITY_TYPE_FOO = EntityType.of("foo");
    private static final EntityType ENTITY_TYPE_BAR = EntityType.of("bar");

    @Test
    public void constructInstanceWithNullBaseTypeFails() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TestEntityIdFactory(null))
                .withMessage("The entityIdBaseType must not be null!")
                .withNoCause();
    }

    @Test
    public void getEntityIdReturnsExpected() {
        final String entityIdValue = "myId";
        final TestEntityIdFactory underTest = new TestEntityIdFactory(EntityId.class);

        final EntityId foundEntityId = underTest.getEntityId(ENTITY_TYPE_FOO, entityIdValue);

        assertThat((CharSequence) foundEntityId).isEqualTo(FooEntityId.of(entityIdValue));
    }

    @Test
    public void getNamespacedEntityIdReturnsExpected() {
        final String entityIdValue = "com.example:myId";
        final TestEntityIdFactory underTest = new TestEntityIdFactory(EntityId.class);

        final EntityId foundEntityId = underTest.getEntityId(ENTITY_TYPE_BAR, entityIdValue);

        assertThat((CharSequence) foundEntityId).isEqualTo(BarEntityId.of(entityIdValue));
    }

    @TypedEntityId(type = "foo")
    private static final class FooEntityId extends AbstractEntityId {

        private FooEntityId(final CharSequence id) {
            super(ENTITY_TYPE_FOO, id);
        }

        public static FooEntityId of(final CharSequence id) {
            return new FooEntityId(id);
        }

    }

    @TypedEntityId(type = "bar")
    private static final class BarEntityId extends AbstractNamespacedEntityId {

        private BarEntityId(final CharSequence id) {
            super(ENTITY_TYPE_BAR, id);
        }

        public static BarEntityId of(final CharSequence id) {
            return new BarEntityId(id);
        }

    }

    private static final class TestEntityIdFactory extends BaseEntityIdFactory<EntityId> {

        TestEntityIdFactory(final Class<EntityId> entityIdBaseType) {
            super(entityIdBaseType);
        }

        @Override
        protected EntityId getFallbackEntityId(final EntityType entityType, final CharSequence entityIdValue) {
            return FallbackEntityId.of(entityType, entityIdValue);
        }

    }

}
