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
package org.eclipse.ditto.edge.service.placeholders;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.placeholders.Placeholder;

/**
 * A {@link org.eclipse.ditto.placeholders.Placeholder} that requires a {@code String}
 * (a valid Entity ID) to resolve its placeholders.
 */
public interface EntityIdPlaceholder extends Placeholder<EntityId> {

    /**
     * Returns the singleton instance of the {@link EntityIdPlaceholder}.
     *
     * @return the singleton instance.
     */
    static EntityIdPlaceholder getInstance() {
        return ImmutableEntityIdPlaceholder.INSTANCE;
    }
}
