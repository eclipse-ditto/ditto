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
package org.eclipse.ditto.internal.utils.test.mongo;

import java.util.Arrays;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ContainerNetworkSettings;
import com.github.dockerjava.api.model.ContainerPort;

/**
 * Provides an easy way to start, stop and remove the once created docker container.
 */
final class DockerContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerContainer.class);
    private static final Integer DOCKER_STOP_TIMEOUT_SECONDS = 5;
    private static final String DEFAULT_DOCKER_HOST = "172.17.0.1";
    private static final String DOCKER_BRIDGE_NETWORK = "bridge";
    private static final String LOCALHOST = "localhost";
    private final DockerClient dockerClient;
    private final String containerId;

    public DockerContainer(final DockerClient dockerClient, final String containerId) {
        this.dockerClient = dockerClient;
        this.containerId = containerId;
    }

    void start() {
        LOGGER.info("Starting docker container with ID <{}>.", containerId);
        dockerClient.startContainerCmd(containerId).exec();
    }

    void stop() {
        LOGGER.info("Stopping docker container with ID <{}>.", containerId);
        dockerClient.stopContainerCmd(containerId).withTimeout(DOCKER_STOP_TIMEOUT_SECONDS).exec();
    }

    void remove() {
        LOGGER.info("Removing docker container with ID <{}>.", containerId);
        dockerClient.removeContainerCmd(containerId).exec();
    }

    /**
     * Translates the given private port (for mongo DB it is for example 27017) to the port it has been bound to
     * on the host machine.
     *
     * @param privatePort the private port (for mongo DB it is for example 27017).
     * @return the bound port.
     * @throws IllegalArgumentException when the given private port was not exposed by this container.
     */
    int getPort(final int privatePort) {
        final Container container = getContainer();
        return Arrays.stream(container.getPorts())
                .filter(containerPort -> isPrivatePortBoundToPublicPort(privatePort, containerPort))
                .findAny()
                .map(ContainerPort::getPublicPort)
                .orElseThrow(() -> {
                    final String message =
                            String.format("No private port <%d> exposed in this docker container.", privatePort);
                    return new IllegalArgumentException(message);
                });
    }

    private static boolean isPrivatePortBoundToPublicPort(final int privatePort, final ContainerPort containerPort) {
        final Integer containerPrivatePort = containerPort.getPrivatePort();
        return containerPrivatePort != null &&
                containerPrivatePort == privatePort &&
                containerPort.getPublicPort() != null;
    }

    String getHostname() {
        return Optional.ofNullable(getContainer().getNetworkSettings())
                .map(ContainerNetworkSettings::getNetworks)
                .map(networks -> networks.get(DOCKER_BRIDGE_NETWORK))
                .map(ContainerNetwork::getGateway)
                .map(hostname -> {
                    if (OsDetector.isMac()) {
                        return LOCALHOST;
                    } else if (OsDetector.isWindows() && DEFAULT_DOCKER_HOST.equals(hostname)) {
                        return LOCALHOST;
                    } else {
                        return hostname;
                    }
                })
                .orElseThrow(
                        () -> new IllegalArgumentException("Could not find a gateway defined for network 'bridge'.")
                );
    }

    private Container getContainer() {
        return dockerClient.listContainersCmd()
                .exec()
                .stream()
                .filter(container -> container.getId().equals(containerId))
                .findAny()
                .orElseThrow(() -> {
                    final String message = String.format("No container with ID <%s> found.", containerId);
                    return new IllegalStateException(message);
                });
    }

}
