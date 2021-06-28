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

import org.eclipse.ditto.base.model.entity.type.EntityType;

/**
 * Factory for getting instances of implementations of {@link EntityId}.
 * This includes instances of {@link NamespacedEntityId} as well.
 *
 * @since 2.1.0
 */
final class EntityIdFactory extends BaseEntityIdFactory<EntityId> {

    private EntityIdFactory() {
        super(EntityId.class);
    }

    /**
     * Returns a new instance of {@code EntityIdFactory}.
     *
     * @return the instance.
     */
    static EntityIdFactory newInstance() {
        return new EntityIdFactory();
    }

    @Override
    protected EntityId getFallbackEntityId(final EntityType entityType, final CharSequence entityIdValue) {
        return FallbackEntityId.of(entityType, entityIdValue);
    }

}
