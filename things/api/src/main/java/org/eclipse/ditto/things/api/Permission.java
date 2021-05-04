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
package org.eclipse.ditto.things.api;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.policies.model.Permissions;

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
