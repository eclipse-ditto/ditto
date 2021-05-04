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
package org.eclipse.ditto.policies.model.enforcers.tree;

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
