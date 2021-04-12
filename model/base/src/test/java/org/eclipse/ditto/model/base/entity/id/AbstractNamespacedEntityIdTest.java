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

import java.util.List;

import javax.annotation.Nullable;

import org.assertj.core.util.Lists;
import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link AbstractNamespacedEntityId}.
 * It uses a dummy implementation as this class only tests commonly implemented methods of NamespacedEntityId.
 */
@RunWith(Parameterized.class)
public final class AbstractNamespacedEntityIdTest {

    private static final EntityType ENTITY_TYPE_PLUMBUS = EntityType.of("plumbus");
    private static final EntityType ENTITY_TYPE_GRUMBO = EntityType.of("grumbo");

    @Parameterized.Parameters(name = "{0}")
    public static List<CompatibilityCheckParameter> testParameters() {
        return Lists.list(
                CompatibilityCheckParameter.compatible(createEntityId("", "baz"), createEntityId("", "baz")),
                CompatibilityCheckParameter.compatible(createEntityId("foo", "bar"), createEntityId("foo", "bar")),
                CompatibilityCheckParameter.compatible(createEntityId("", "baz"), createEntityId("foo", "baz")),
                CompatibilityCheckParameter.compatible(createEntityId("foo", "baz"), createEntityId("", "baz")),
                CompatibilityCheckParameter.incompatible(createEntityId("foo", "baz"), null),
                CompatibilityCheckParameter.incompatible(createEntityId("", "baz"), createEntityId("", "bar")),
                CompatibilityCheckParameter.incompatible(createEntityId("foo", "baz"), createEntityId("", "bar")),
                CompatibilityCheckParameter.incompatible(createEntityId("", "baz"), createEntityId("baz", "bar")),
                CompatibilityCheckParameter.incompatible(createEntityId("", "foo"), createEntityId("foo", "bar")),
                CompatibilityCheckParameter.incompatible(createEntityId("foo", "baz"), createEntityId("", "bar")),
                CompatibilityCheckParameter.incompatible(createEntityId("foo", "bar"), createEntityId("foo", "baz")),
                CompatibilityCheckParameter.incompatible(createEntityId("foo", "baz"), createEntityId("bar", "baz")),
                CompatibilityCheckParameter.incompatible(createEntityId("foo", "bar"), createEntityId("bar", "foo")),
                CompatibilityCheckParameter.incompatible(createEntityId(ENTITY_TYPE_PLUMBUS, "foo", "bar"),
                        createEntityId(ENTITY_TYPE_GRUMBO, "foo", "bar"))
        );
    }

    @Parameterized.Parameter
    public CompatibilityCheckParameter parameter;

    @Test
    public void isCompatibleBehavesCorrectly() {
        assertThat(parameter.blue.isCompatible(parameter.green)).isEqualTo(parameter.shouldBeCompatible);
    }

    private static AbstractNamespacedEntityId createEntityId(@Nullable final CharSequence namespace,
            final CharSequence name) {

        return createEntityId(ENTITY_TYPE_PLUMBUS, namespace, name);
    }

    private static AbstractNamespacedEntityId createEntityId(final EntityType entityType,
            @Nullable final CharSequence namespace, final CharSequence name) {

        return DummyImplementation.of(entityType, name, namespace);
    }

    private static final class DummyImplementation extends AbstractNamespacedEntityId {

        private final EntityType entityType;

        private DummyImplementation(final EntityType entityType, final NamespacedEntityId namespacedEntityId) {
            super(namespacedEntityId);
            this.entityType = entityType;
        }

        static DummyImplementation of(final EntityType entityType, final CharSequence name,
                @Nullable final CharSequence namespace) {

            final NamespacedEntityId namespacedEntityId;
            if (null == namespace) {
                namespacedEntityId = DefaultNamespacedEntityId.fromName(entityType, name.toString());
            } else {
                namespacedEntityId = DefaultNamespacedEntityId.of(entityType, namespace.toString(), name.toString());
            }
            return new DummyImplementation(entityType, namespacedEntityId);
        }

        @Override
        public EntityType getEntityType() {
            return entityType;
        }

    }

    private static final class CompatibilityCheckParameter {

        private final AbstractNamespacedEntityId blue;
        @Nullable private final AbstractNamespacedEntityId green;
        private final boolean shouldBeCompatible;

        private CompatibilityCheckParameter(final AbstractNamespacedEntityId blue,
                @Nullable final AbstractNamespacedEntityId green, final boolean shouldBeCompatible) {

            this.blue = blue;
            this.green = green;
            this.shouldBeCompatible = shouldBeCompatible;
        }

        static CompatibilityCheckParameter compatible(final AbstractNamespacedEntityId blue,
                final AbstractNamespacedEntityId green) {

            return new CompatibilityCheckParameter(blue, green, true);
        }

        static CompatibilityCheckParameter incompatible(final AbstractNamespacedEntityId blue,
                @Nullable final AbstractNamespacedEntityId green) {

            return new CompatibilityCheckParameter(blue, green, false);
        }

        @Override
        public String toString() {
            return "blue=" + blue + "," + blue.getEntityType() +
                    "; green=" + green + (null != green ? "," + green.getEntityType() : "null") +
                    "; expected=" + (shouldBeCompatible ? "compatible" : "incompatible") + "]";
        }
    }

}
