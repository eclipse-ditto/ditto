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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.junit.Test;
/**
 * Unit test for {@link EntityIdWithTypeEqualityValidator}.
 */
public final class EntityIdWithTypeEqualityValidatorTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(EntityIdWithTypeEqualityValidator.class, areImmutable());
    }

    @Test
    public void acceptSameId() {
        final MyEntityId entityId = new MyEntityId("foo");
        final EntityIdWithTypeEqualityValidator underTest = EntityIdWithTypeEqualityValidator.getInstance(entityId);

        assertThatCode(() -> underTest.accept(entityId)).doesNotThrowAnyException();
    }

    @Test
    public void acceptDifferentIds() {
        final MyEntityId entityIdFoo = new MyEntityId("foo");
        final MyEntityId entityIdBar = new MyEntityId("bar");
        final EntityIdWithTypeEqualityValidator underTest = EntityIdWithTypeEqualityValidator.getInstance(entityIdFoo);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.accept(entityIdBar))
                .withMessage("The entity ID <%s> differs from the expected <%s>!", entityIdBar, entityIdFoo)
                .withNoCause();
    }

    private static final class MyEntityId extends EntityIdWithType {

        MyEntityId(final CharSequence id) {
            super(DefaultEntityId.of(id));
        }

        @Override
        public EntityType getEntityType() {
            return EntityType.of("plumbus");
        }

    }

}
