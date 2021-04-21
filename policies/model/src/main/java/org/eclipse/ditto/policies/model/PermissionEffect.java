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
package org.eclipse.ditto.policies.model;

import javax.annotation.concurrent.Immutable;

/**
 * An enumeration of the available {@link Policy} permission effects.
 */
@Immutable
public enum PermissionEffect {

    /**
     * Effect granting the contained {@link Permissions}.
     */
    GRANT("grant"),

    /**
     * Effect revoking the contained {@link Permissions}.
     */
    REVOKE("revoke");

    private final String id;

    private PermissionEffect(final String id) {
        this.id = id;
    }

    /**
     * Retrieve the ID of the Permission effect.
     *
     * @return the ID of the Permission effect.
     */
    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return id;
    }

}
