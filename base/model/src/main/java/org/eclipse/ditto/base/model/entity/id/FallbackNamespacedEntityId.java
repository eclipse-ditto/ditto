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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.type.EntityType;

/**
 * Fallback implementation of a namespaced entity ID.
 */
@Immutable
final class FallbackNamespacedEntityId extends AbstractNamespacedEntityId {

    private FallbackNamespacedEntityId(final EntityType entityType, final CharSequence id) {
        super(entityType, id);
    }

    /**
     * Returns an instance of this class based on the given entity type and ID.
     *
     * @param entityType the type of the entity this ID identifies.
     * @param entityId the entity ID.
     * @return the Namespaced Entity ID instance.
     * @throws NullPointerException if {@code entityType} is null.
     * @throws org.eclipse.ditto.base.model.entity.id.NamespacedEntityIdInvalidException if {@code entityId} is {@code null}.
     * @throws org.eclipse.ditto.base.model.entity.id.NamespacedEntityIdInvalidException if {@code entityId} is empty.
     */
    static FallbackNamespacedEntityId of(final EntityType entityType, final CharSequence entityId) {
        return new FallbackNamespacedEntityId(entityType, entityId);
    }

}
