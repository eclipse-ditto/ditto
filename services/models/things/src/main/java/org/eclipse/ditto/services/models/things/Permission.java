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
package org.eclipse.ditto.services.models.things;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.policies.Permissions;

/**
 * An enumeration of known permissions of the Thing store.
 */
@Immutable
public final class Permission {

    /**
     * Permission to read an entity.
     */
    public static final String READ = "READ";

    /**
     * Permission to write/change an entity.
     */
    public static final String WRITE = "WRITE";

    /**
     * The set of Permissions which must be set as default on the 'thing:/' Resource for the current Subject,
     * if no policy is present.
     */
    @SuppressWarnings({"squid:S2386"})
    public static final Permissions DEFAULT_THING_PERMISSIONS = Permissions.newInstance(READ, WRITE);

    private Permission() {
        throw new AssertionError();
    }

}
