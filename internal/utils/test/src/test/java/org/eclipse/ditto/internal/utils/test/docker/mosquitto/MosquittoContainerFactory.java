/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.test.docker.mosquitto;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

import com.github.dockerjava.api.model.AccessMode;
import com.google.common.base.MoreObjects;
import org.eclipse.ditto.internal.utils.test.docker.ContainerFactory;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;

/**
 * Responsible for creating and configuring the mosquitto docker container that should be started for tests.
 */
final class MosquittoContainerFactory extends ContainerFactory {

    private static final String MOSQUITTO_IMAGE_NAME = "eclipse-mosquitto";
    private static final String MOSQUITTO_VERSION = "2.0.18";
    private static final int MOSQUITTO_INTERNAL_PORT = 1883;
    private static final String CONFIG_CONTAINER_PATH = "/mosquitto/config/mosquitto.conf";
    private final String CONFIG_RESOURCES_PATH;

    private MosquittoContainerFactory(final String configResourceName, final String mosquittoVersion) {
        super(MOSQUITTO_IMAGE_NAME + ":" + mosquittoVersion, MOSQUITTO_INTERNAL_PORT);

        try {
            CONFIG_RESOURCES_PATH = getResourceAbsolutePath(configResourceName);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private String getResourceAbsolutePath(String resourceName) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource(resourceName)).toURI()).toAbsolutePath().toString();
    }

    /**
     * @return returns the singleton instance of this factory.
     */
    static MosquittoContainerFactory of(final String configResourceName) {
        return new MosquittoContainerFactory(configResourceName, MOSQUITTO_VERSION);
    }

    @Override
    protected CreateContainerCmd configureContainer(CreateContainerCmd createContainerCmd) {
        final var hostConfig = MoreObjects.firstNonNull(createContainerCmd.getHostConfig(), HostConfig.newHostConfig())
                .withBinds(new Bind(CONFIG_RESOURCES_PATH, new Volume(CONFIG_CONTAINER_PATH), AccessMode.ro));
        return createContainerCmd.withHostConfig(hostConfig);
    }
}
