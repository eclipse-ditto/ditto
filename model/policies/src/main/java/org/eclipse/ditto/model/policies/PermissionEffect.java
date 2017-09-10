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
package org.eclipse.ditto.model.policies;

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
