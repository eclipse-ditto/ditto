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
package org.eclipse.ditto.internal.utils.persistence.api;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import com.typesafe.config.Config;

/**
 * Boot-time hook of a persistence backend, discovered via {@link ServiceLoader}.
 * <p>
 * Every bootstrap found on the classpath may adjust the raw service configuration before the ActorSystem is created,
 * e.g. to inject backend-specific Pekko plugin settings that are derived from the Ditto configuration.
 * <p>
 * Implementations are registered in
 * {@code META-INF/services/org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendBootstrap}. They must
 * be stateless, have a public no-arg constructor and must not change anything except by returning a new
 * {@link Config}.
 */
public interface PersistenceBackendBootstrap {

    /**
     * Returns the stable, lowercase, non-empty name of the backend, e.g. {@code "mongodb"} or {@code "postgresql"}.
     * It equals the backend's folder name below {@code /opt/ditto/backends/}.
     *
     * @return the backend name.
     */
    String backendName();

    /**
     * Adjusts the raw configuration of the service. Called before the ActorSystem exists.
     *
     * @param rawConfig the raw configuration of the service.
     * @return {@code rawConfig} itself or a {@code Config} derived from it, never {@code null}.
     * @throws NullPointerException if {@code rawConfig} is {@code null}.
     */
    Config adjustRawConfig(Config rawConfig);

    /**
     * Instantiates every bootstrap registered for the given class loader, in {@link ServiceLoader} iteration order.
     * A {@link java.util.ServiceConfigurationError} of the {@code ServiceLoader} is propagated unchanged.
     *
     * @param classLoader the class loader to look up the registered bootstraps with.
     * @return an unmodifiable list of the registered bootstraps, empty if none is registered.
     * @throws NullPointerException if {@code classLoader} is {@code null}.
     */
    static List<PersistenceBackendBootstrap> loadAll(final ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        return ServiceLoader.load(PersistenceBackendBootstrap.class, classLoader)
                .stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toUnmodifiableList());
    }

}
