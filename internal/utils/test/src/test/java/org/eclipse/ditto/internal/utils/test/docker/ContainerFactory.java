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
package org.eclipse.ditto.internal.utils.test.docker;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

/**
 * Responsible for creating and configuring the mongo db docker container that should be started for tests.
 */
public abstract class ContainerFactory implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerFactory.class);

    private static final String UNIX_DOCKER_HOST = "unix:///var/run/docker.sock";
    private static final String WINDOWS_DOCKER_HOST = "npipe:////./pipe/docker_engine";

    private final String imageIdentifier;
    private final int[] ports;
    private final DockerClient dockerClient;

    protected ContainerFactory(final String imageIdentifier, final int... ports) {
        this.imageIdentifier = imageIdentifier;
        this.ports = ports;
        final String dockerHost = OsDetector.isWindows() ? WINDOWS_DOCKER_HOST : UNIX_DOCKER_HOST;
        LOGGER.info("Connecting to docker daemon on <{}>.", dockerHost);
        final DefaultDockerClientConfig config =
                DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(dockerHost).build();
        final ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        dockerClient = DockerClientImpl.getInstance(config, httpClient);

        LOGGER.info("Checking if image <{}> needs to be pulled.", imageIdentifier);
        if (isImageAbsent()) {
            pullImage();
        }
    }

    protected CreateContainerCmd configureContainer(final CreateContainerCmd createContainerCmd) {
        return createContainerCmd;
    }

    /**
     * Creates the docker container with all required configuration and returns it.
     * It's not started after it has been returned.
     *
     * @return the created {@link DockerContainer}.
     */
    DockerContainer createContainer() {
        return getImageId()
                .map(imageId -> {
                    LOGGER.info("Creating container based on image with ID <{}>.", imageId);
                    final var createContainerCmd = dockerClient.createContainerCmd(imageId)
                            .withHostConfig(
                                    HostConfig.newHostConfig().withPortBindings(Arrays.stream(ports)
                                            .mapToObj(p -> new PortBinding(Ports.Binding.empty(), ExposedPort.tcp(p)))
                                            .collect(Collectors.toList())));
                    return configureContainer(createContainerCmd)
                            .exec()
                            .getId();
                })
                .map(containerId -> new DockerContainer(dockerClient, containerId))
                .orElseThrow(
                        () -> new IllegalStateException("Could not create container because no image was present.")
                );
    }

    private boolean isImageAbsent() {
        final Optional<String> imageId = getImageId();
        imageId.ifPresentOrElse(id -> {
            LOGGER.info("Image <{}> is already present with ID <{}>", imageIdentifier, id);
        }, () -> {
            LOGGER.info("Image <{}> is not present, yet.", imageIdentifier);
        });
        return imageId.isEmpty();
    }

    private Optional<String> getImageId() {
        try {
            return Optional.ofNullable(dockerClient.inspectImageCmd(imageIdentifier).exec().getId());
        } catch (final NotFoundException e) {
            return Optional.empty();
        }
    }

    private void pullImage() {
        LOGGER.info("Pulling <{}>.", imageIdentifier);
        final DockerImagePullHandler dockerImagePullHandler = DockerImagePullHandler.newInstance();
        dockerClient.pullImageCmd(imageIdentifier).exec(dockerImagePullHandler);
        dockerImagePullHandler.getImagePullFuture().join();
    }

    @Override
    public void close() throws IOException {
        dockerClient.close();
    }
}
