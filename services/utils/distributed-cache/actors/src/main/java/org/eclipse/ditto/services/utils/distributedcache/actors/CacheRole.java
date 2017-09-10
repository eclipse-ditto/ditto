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
package org.eclipse.ditto.services.utils.distributedcache.actors;

/**
 * An enumeration of CacheRoles supported by {@link CacheFacadeActor}.
 */
public enum CacheRole implements CharSequence {

    /**
     * Cache role for Things.
     */
    THING("thing"),

    /**
     * Cache role for Policies.
     */
    POLICY("policy"),

    /**
     * Cache role for Topologies.
     */
    TOPOLOGY("topology"),

    /**
     * Cache role for Topology Schemas.
     */
    TOPOLOGY_SCHEMA("topologyschema");

    private final String name;

    CacheRole(final String name) {
        this.name = name;
    }

    @Override
    public int length() {
        return name.length();
    }

    @Override
    public char charAt(final int index) {
        return name.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return name.subSequence(start, end);
    }

    @Override
    public String toString() {
        return name;
    }

}
