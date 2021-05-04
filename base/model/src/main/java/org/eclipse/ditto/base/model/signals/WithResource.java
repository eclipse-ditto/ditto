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
package org.eclipse.ditto.base.model.signals;

import org.eclipse.ditto.json.JsonPointer;

/**
 * Implementations of this interface are associated with an entity represented by a resource path and type.
 */
public interface WithResource {

    /**
     * Returns the path of the {@code Resource} represented by this entity.
     *
     * @return the path.
     */
    JsonPointer getResourcePath();

    /**
     * Returns the type of the {@code Resource} represented by this entity.
     *
     * @return the type.
     */
    String getResourceType();

}
