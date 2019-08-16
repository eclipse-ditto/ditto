/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.base;

import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;

/**
 * This is a temporary fix for the use of the {@link WithId} interface where it should not have been used because
 * actually there is not an ID.
 * We should re-consider the use of {@link WithId}.
 */
public interface WithIdButActuallyNot extends WithId {

    /**
     * Returns the identifier of the entity.
     *
     * @return the identifier of the entity.
     * @deprecated Entity IDs are now typed. Use {@link #getEntityId()} instead.
     */
    @Deprecated
    @Override
    default String getId() {
        return DefaultEntityId.placeholder().toString();
    }

    /**
     * Returns the identifier of the entity.
     *
     * @return the identifier of the entity.
     */
    @Override
    default EntityId getEntityId() {
        return DefaultEntityId.placeholder();
    }
}
