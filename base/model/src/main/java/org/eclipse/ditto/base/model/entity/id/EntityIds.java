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

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.entity.type.EntityType;

/**
 * Factory class to instantiate the correct entity type based on the given {@link org.eclipse.ditto.base.model.entity.type.EntityType}.
 * Uses Reflection to find all EntityIds annotated with {@link org.eclipse.ditto.base.model.entity.id.TypedEntityId} and
 * expects one static method which accepts a {@link CharSequence} and returns something which is a subtype of itself.
 */
final class EntityIds {

    @Nullable private static EntityIds instance = null;

    private final BaseEntityIdFactory<EntityId> entityIdFactory;
    private final BaseEntityIdFactory<NamespacedEntityId> namespacedEntityIdFactory;

    private EntityIds(final BaseEntityIdFactory<EntityId> entityIdFactory,
            final BaseEntityIdFactory<NamespacedEntityId> namespacedEntityIdFactory) {

        this.entityIdFactory = entityIdFactory;
        this.namespacedEntityIdFactory = namespacedEntityIdFactory;
    }

    /**
     * Returns an <em>singleton</em> instance of {@code EntityIds}.
     *
     * @return the singleton instance.
     */
    static EntityIds getInstance() {
        EntityIds result = instance;
        if (null == result) {
            result = newInstance(EntityIdFactory.newInstance(), NamespacedEntityIdFactory.newInstance());
            instance = result;
        }
        return result;
    }

    /**
     * Returns a new instance of {@code EntityIds}.
     *
     * @param entityIdFactory factory for creating {@link EntityId}s with known entity types
     * (including {@code NamespacedEntityId}s).
     * @param namespacedEntityIdFactory factory for creating {@link NamespacedEntityId}s with known entity types.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static EntityIds newInstance(final BaseEntityIdFactory<EntityId> entityIdFactory,
            final BaseEntityIdFactory<NamespacedEntityId> namespacedEntityIdFactory) {

        return new EntityIds(ConditionChecker.checkNotNull(entityIdFactory, "entityIdFactory"),
                ConditionChecker.checkNotNull(namespacedEntityIdFactory, "namespacedEntityIdFactory"));
    }

    /**
     * Best effort to initialize the most concrete type of a {@link org.eclipse.ditto.base.model.entity.id.NamespacedEntityId}
     * based on the given entityType.
     *
     * @param entityType the type of the entity which should be identified by the given entity ID.
     * @param entityIdValue the ID of an entity.
     * @return the namespaced entity ID.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws EntityIdInvalidException if {@code entityIdValue} represents an invalid ID for {@code entityType}.
     */
    public NamespacedEntityId getNamespacedEntityId(final EntityType entityType, final CharSequence entityIdValue) {
        return namespacedEntityIdFactory.getEntityId(entityType, entityIdValue);
    }

    /**
     * Best effort to initialize the most concrete type of a {@link org.eclipse.ditto.base.model.entity.id.EntityId}
     * based on the given entityType.
     *
     * @param entityType The type of the entity which should be identified by the given entity ID.
     * @param entityIdValue The ID of an entity.
     * @return the entity ID.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws EntityIdInvalidException if {@code entityIdValue} represents an invalid ID for {@code entityType}.
     */
    public EntityId getEntityId(final EntityType entityType, final CharSequence entityIdValue) {
        return entityIdFactory.getEntityId(entityType, entityIdValue);
    }

}
