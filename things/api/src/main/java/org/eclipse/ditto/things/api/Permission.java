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
     * Permission to read timeseries data of a Thing's properties.
     * <p>
     * Applies only to {@code thing:/...} resources and gates retrieval of historical timeseries data via the
     * Timeseries API. The Timeseries service exposes a configurable
     * {@code ditto.timeseries.enforcement.required-permission} which determines how this permission is
     * interpreted at enforcement time:
     * <ul>
     *   <li><b>{@code READ_TS} (fine-grained, default):</b> the subject must have an explicit {@code READ_TS}
     *       grant on the requested resource. {@link #READ} alone is <em>not</em> sufficient. An explicit
     *       {@code READ_TS} revoke at a more specific path takes precedence over a {@code READ_TS} grant at
     *       a parent path, following Ditto's standard policy resolution.</li>
     *   <li><b>{@code READ} (simplified):</b> a {@link #READ} grant on the requested resource is sufficient
     *       to read timeseries data; no separate {@code READ_TS} grant is required. In this mode an explicit
     *       {@code READ_TS} revoke is a no-op because the enforcer does not look up {@code READ_TS}.</li>
     * </ul>
     * <p>
     * Note: {@code READ_TS} is intentionally <em>not</em> included in {@link #DEFAULT_THING_PERMISSIONS}.
     * Subjects that own a Thing without an explicit policy do not implicitly gain timeseries read access;
     * timeseries access is opt-in via policy.
     *
     * @see <a href="https://github.com/eclipse-ditto/ditto/issues/2291">Ditto issue #2291</a>
     */
    public static final String READ_TS = "READ_TS";

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
