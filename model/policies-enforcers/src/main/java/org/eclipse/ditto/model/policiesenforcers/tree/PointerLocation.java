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
package org.eclipse.ditto.model.policiesenforcers.tree;

/**
 * An enumeration of relative {@link org.eclipse.ditto.json.JsonPointer} locations.
 */
enum PointerLocation {

    /**
     * Indicates that a pointer is disjoint with a compared pointer.
     */
    DIFFERENT,

    /**
     * Indicates that a pointer is the same as a compared pointer.
     */
    ABOVE,

    /**
     * Indicates that a pointer is a parent of a compared pointer.
     */
    SAME,

    /**
     * Indicates that a pointer is a child a child of a compared pointer.
     */
    BELOW

}
