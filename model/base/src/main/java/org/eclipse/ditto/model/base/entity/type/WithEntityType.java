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
package org.eclipse.ditto.model.base.entity.type;

/**
 * This interface provides a uniform way to get the {@link EntityType}.
 *
 * @since 1.1.0
 */
public interface WithEntityType {

    /**
     * Returns the entity type.
     *
     * @return the entity type.
     */
    EntityType getEntityType();

}
