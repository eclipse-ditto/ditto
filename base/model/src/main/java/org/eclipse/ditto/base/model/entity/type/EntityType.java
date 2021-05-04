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
package org.eclipse.ditto.base.model.entity.type;

import javax.annotation.concurrent.Immutable;

/**
 * This class represents the type of an Entity.
 * <em>Implementations of this class are required to be immutable!</em>
 *
 * @since 1.1.0
 */
@Immutable
public interface EntityType extends Comparable<EntityType>,  CharSequence {

    /**
     * Returns an instance of EntityType based on the given CharSequence.
     *
     * @param value the value of the entity type.
     * @return the instance.
     * @throws NullPointerException if {@code value} is {@code null}.
     * @throws IllegalArgumentException if {@code value} is empty.
     */
    static EntityType of(final CharSequence value) {
        return EntityTypes.getDefaultEntityType(value);
    }

    /**
     * Returns the value of this entity type.
     *
     * @return the value.
     */
    @Override
    String toString();

}
