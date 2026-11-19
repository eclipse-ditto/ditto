/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.postgres.client;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendBootstrap;

import com.typesafe.config.Config;

/**
 * PostgreSQL implementation of {@link PersistenceBackendBootstrap}.
 * <p>
 * PostgreSQL needs no configuration before the actor system starts, so {@link #adjustRawConfig(Config)} returns its
 * argument. The class exists so that the boot check of the persistence services counts PostgreSQL as a backend. It
 * ships in the PostgreSQL client JAR, which every PostgreSQL deployment carries.
 * </p>
 */
@Immutable
public final class PostgresPersistenceBackendBootstrap implements PersistenceBackendBootstrap {

    /**
     * The name of the PostgreSQL backend.
     */
    public static final String BACKEND_NAME = "postgresql";

    /**
     * Constructs a new {@code PostgresPersistenceBackendBootstrap}; called by the {@link java.util.ServiceLoader}.
     */
    public PostgresPersistenceBackendBootstrap() {
        super();
    }

    @Override
    public String backendName() {
        return BACKEND_NAME;
    }

    @Override
    public Config adjustRawConfig(final Config rawConfig) {
        return Objects.requireNonNull(rawConfig, "rawConfig");
    }

}
