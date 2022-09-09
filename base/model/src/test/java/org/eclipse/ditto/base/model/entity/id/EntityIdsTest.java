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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link EntityIds}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class EntityIdsTest {

    @Mock
    private BaseEntityIdFactory<EntityId> entityIdFactory;

    @Mock
    private BaseEntityIdFactory<NamespacedEntityId> namespacedEntityIdFactory;

    @Test
    public void assertImmutability() {
        assertInstancesOf(EntityIds.class, areImmutable(), provided(BaseEntityIdFactory.class).isAlsoImmutable());
    }

    @Test
    public void getInstanceReturnsSameInstance() {
        final EntityIds instanceOfFirstCall = EntityIds.getInstance();
        final EntityIds instanceOfSecondCall = EntityIds.getInstance();

        assertThat(instanceOfFirstCall).isSameAs(instanceOfSecondCall);
    }

    @Test
    public void newInstanceWithNullEntityIdFactoryFails() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> EntityIds.newInstance(null, namespacedEntityIdFactory))
                .withMessage("The entityIdFactory must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullNamespacedEntityIdFactoryFails() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> EntityIds.newInstance(entityIdFactory, null))
                .withMessage("The namespacedEntityIdFactory must not be null!")
                .withNoCause();
    }

    @Test
    public void getNamespacedEntityIdDelegatesToNamespacedEntityIdFactory() {
        final EntityType entityType = EntityType.of("fleeb");
        final String entityIdValue = "com.example:froob";
        final EntityIds underTest = EntityIds.newInstance(entityIdFactory, namespacedEntityIdFactory);

        underTest.getNamespacedEntityId(entityType, entityIdValue);

        Mockito.verify(namespacedEntityIdFactory).getEntityId(Mockito.eq(entityType), Mockito.eq(entityIdValue));
        Mockito.verifyNoInteractions(entityIdFactory);
    }

    @Test
    public void getEntityIdDelegatesToEntityIdFactory() {
        final EntityType entityType = EntityType.of("fleeb");
        final String entityIdValue = "froob";
        final EntityIds underTest = EntityIds.newInstance(entityIdFactory, namespacedEntityIdFactory);

        underTest.getEntityId(entityType, entityIdValue);

        Mockito.verify(entityIdFactory).getEntityId(Mockito.eq(entityType), Mockito.eq(entityIdValue));
        Mockito.verifyNoInteractions(namespacedEntityIdFactory);
    }

}