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
package org.eclipse.ditto.policies.api;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.policies.model.Permissions;

/**
 * An enumeration of known permissions of the policies service.
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
     * Permission to execute actions.
     */
    public static final String EXECUTE = "EXECUTE";

    /**
     * The set of Permissions which must be present on the 'policy:/' Resource for at least one Subject.
     */
    @SuppressWarnings({"squid:S2386"})
    public static final Permissions MIN_REQUIRED_POLICY_PERMISSIONS = Permissions.newInstance(WRITE);

    /**
     * The set of Permissions which must be set as default on the 'policy:/' Resource for the current Subject,
     * if no policy is present.
     */
    @SuppressWarnings({"squid:S2386"})
    public static final Permissions DEFAULT_POLICY_PERMISSIONS = Permissions.newInstance(READ, WRITE);

    private Permission() {
        throw new AssertionError();
    }

}
