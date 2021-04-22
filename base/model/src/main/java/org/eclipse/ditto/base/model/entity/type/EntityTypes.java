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
 * A factory that provides instances of {@link org.eclipse.ditto.base.model.entity.type.EntityType}.
 *
 * @since 1.1.0
 */
@Immutable
final class EntityTypes {

    private EntityTypes() {
        throw new AssertionError();
    }

    /**
     * Returns an instance of the default entity type for the given CharSequence.
     *
     * @param value the value of the entity type.
     * @return the instance.
     * @throws NullPointerException if {@code value} is {@code null}.
     * @throws IllegalArgumentException if {@code value} is empty.
     */
    public static DefaultEntityType getDefaultEntityType(final CharSequence value) {
        return DefaultEntityType.of(value);
    }

}
