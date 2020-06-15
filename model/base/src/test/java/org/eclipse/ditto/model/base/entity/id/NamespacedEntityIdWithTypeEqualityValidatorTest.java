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
 * Unit test for {@link NamespacedEntityIdWithTypeEqualityValidator}.
 */
public final class NamespacedEntityIdWithTypeEqualityValidatorTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(NamespacedEntityIdWithTypeEqualityValidator.class, areImmutable());
    }

    @Test
    public void acceptSameId() {
        final NamespacedEntityIdWithType entityId = createEntityId("foo", "bar");
        final NamespacedEntityIdWithTypeEqualityValidator underTest =
                NamespacedEntityIdWithTypeEqualityValidator.getInstance(entityId);

        assertThatCode(() -> underTest.accept(entityId)).doesNotThrowAnyException();
    }

    @Test
    public void acceptDifferentIds() {
        final NamespacedEntityIdWithType expected = createEntityId("foo", "bar");
        final NamespacedEntityIdWithType actual = createEntityId("bar", "foo");
        final NamespacedEntityIdWithTypeEqualityValidator underTest =
                NamespacedEntityIdWithTypeEqualityValidator.getInstance(expected);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.accept(actual))
                .withMessage("The entity ID <%s> differs from the expected <%s>!", actual,
                        expected)
                .withNoCause();
    }

    @Test
    public void acceptSameNameButDifferentNamespace() {
        final NamespacedEntityIdWithType expected = createEntityId("foo", "baz");
        final NamespacedEntityIdWithType actual = createEntityId("bar", "baz");
        final NamespacedEntityIdWithTypeEqualityValidator underTest =
                NamespacedEntityIdWithTypeEqualityValidator.getInstance(expected);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.accept(actual))
                .withMessage("The entity ID <%s> differs from the expected <%s>!", actual,
                        expected)
                .withNoCause();
    }

    @Test
    public void acceptSameNameButExpectedHasEmptyNamespace() {
        final NamespacedEntityIdWithType expected = createEntityId("", "baz");
        final NamespacedEntityIdWithType actual = createEntityId("foo", "baz");
        final NamespacedEntityIdWithTypeEqualityValidator underTest =
                NamespacedEntityIdWithTypeEqualityValidator.getInstance(expected);

        assertThatCode(() -> underTest.accept(actual)).doesNotThrowAnyException();
    }

    @Test
    public void acceptSameNameButActualHasEmptyNamespace() {
        final NamespacedEntityIdWithType expected = createEntityId("foo", "baz");
        final NamespacedEntityIdWithType actual = createEntityId("", "baz");
        final NamespacedEntityIdWithTypeEqualityValidator underTest =
                NamespacedEntityIdWithTypeEqualityValidator.getInstance(expected);

        assertThatCode(() -> underTest.accept(actual)).doesNotThrowAnyException();
    }

    private static NamespacedEntityIdWithType createEntityId(final String namespace, final String name) {
        return new NamespacedEntityIdWithType(DefaultNamespacedEntityId.of(namespace, name)) {
            @Override
            public EntityType getEntityType() {
                return EntityType.of("foobar");
            }
        };
    }
}
