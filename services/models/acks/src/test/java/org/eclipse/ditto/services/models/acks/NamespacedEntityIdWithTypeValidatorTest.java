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
package org.eclipse.ditto.services.models.acks;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.things.ThingId;
import org.junit.Test;

/**
 * Unit test for {@link NamespacedEntityIdWithTypeValidator}.
 */
public final class NamespacedEntityIdWithTypeValidatorTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(NamespacedEntityIdWithTypeValidator.class, areImmutable());
    }

    @Test
    public void acceptSameId() {
        final ThingId entityId = ThingId.of("foo", "bar");
        final NamespacedEntityIdWithTypeValidator underTest = NamespacedEntityIdWithTypeValidator.getInstance(entityId);

        assertThatCode(() -> underTest.accept(entityId)).doesNotThrowAnyException();
    }

    @Test
    public void acceptDifferentIds() {
        final ThingId expected = ThingId.of("foo", "bar");
        final ThingId actual = ThingId.of("bar", "foo");
        final NamespacedEntityIdWithTypeValidator underTest = NamespacedEntityIdWithTypeValidator.getInstance(expected);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.accept(actual))
                .withMessage("The received Acknowledgement's entity ID <%s> differs from the expected <%s>!", actual,
                        expected)
                .withNoCause();
    }

    @Test
    public void acceptSameNameButDifferentNamespace() {
        final ThingId expected = ThingId.of("foo", "baz");
        final ThingId actual = ThingId.of("bar", "baz");
        final NamespacedEntityIdWithTypeValidator underTest = NamespacedEntityIdWithTypeValidator.getInstance(expected);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.accept(actual))
                .withMessage("The received Acknowledgement's entity ID <%s> differs from the expected <%s>!", actual,
                        expected)
                .withNoCause();
    }

    @Test
    public void acceptSameNameButExpectedHasEmptyNamespace() {
        final ThingId expected = ThingId.of("", "baz");
        final ThingId actual = ThingId.of("foo", "baz");
        final NamespacedEntityIdWithTypeValidator underTest = NamespacedEntityIdWithTypeValidator.getInstance(expected);

        assertThatCode(() -> underTest.accept(actual)).doesNotThrowAnyException();
    }

    @Test
    public void acceptSameNameButActualHasEmptyNamespace() {
        final ThingId expected = ThingId.of("foo", "baz");
        final ThingId actual = ThingId.of("", "baz");
        final NamespacedEntityIdWithTypeValidator underTest = NamespacedEntityIdWithTypeValidator.getInstance(expected);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.accept(actual))
                .withMessage("The received Acknowledgement's entity ID <%s> differs from the expected <%s>!", actual,
                        expected)
                .withNoCause();
    }


}
