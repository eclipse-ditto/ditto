/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.base;

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
