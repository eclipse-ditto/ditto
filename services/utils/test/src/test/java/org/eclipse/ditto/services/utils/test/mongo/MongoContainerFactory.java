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
package org.eclipse.ditto.services.utils.test.mongo;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

/**
 * Responsible for creating and configuring the mongo db docker container that should be started for tests.
 */
final class MongoContainerFactory {

    private static final String MONGO_IMAGE_NAME = "mongo";
    private static final String MONGO_VERSION = "4.2";
    private static final String MONGO_IMAGE_IDENTIFIER = MONGO_IMAGE_NAME + ":" + MONGO_VERSION;
    private static final int MONGO_INTERNAL_PORT = 27017;
    private static final PortBinding MONGO_PORT_BINDING_TO_RANDOM_PORT =
            new PortBinding(Ports.Binding.empty(), ExposedPort.tcp(MONGO_INTERNAL_PORT));
    private static final List<String> MONGO_COMMANDS = Arrays.asList("mongod", "--storageEngine", "wiredTiger");

    private static final MongoContainerFactory INSTANCE = new MongoContainerFactory();
    private static final String UNIX_DOCKER_HOST = "unix:///var/run/docker.sock";
    private static final String WINDOWS_DOCKER_HOST = "tcp://localhost:2375";

    private final DockerClient dockerClient;

    private MongoContainerFactory() {
        final String dockerHost = OsDetector.isWindows() ? WINDOWS_DOCKER_HOST : UNIX_DOCKER_HOST;
        final DefaultDockerClientConfig config =
                DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(dockerHost).build();
        final ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        dockerClient = DockerClientImpl.getInstance(config, httpClient);

        if (isMongoImageAbsent()) {
            pullMongoImage();
        }
    }

    /**
     * @return returns the singleton instance of this factory.
     */
    static MongoContainerFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Creates the mongo docker container with all required configuration and returns it.
     * It's not started after it has been returned.
     *
     * @return the created {@link DockerContainer}.
     */
    DockerContainer createMongoContainer() {
        return getMongoImageId()
                .map(imageId -> dockerClient.createContainerCmd(imageId)
                        .withCmd(MONGO_COMMANDS)
                        .withHostConfig(HostConfig.newHostConfig().withPortBindings(MONGO_PORT_BINDING_TO_RANDOM_PORT))
                        .exec()
                        .getId())
                .map(containerId -> new DockerContainer(dockerClient, containerId))
                .orElseThrow(
                        () -> new IllegalStateException("Could not create container because no image was present.")
                );
    }

    private boolean isMongoImageAbsent() {
        return getMongoImageId().isEmpty();
    }

    private Optional<String> getMongoImageId() {
        return dockerClient.listImagesCmd()
                .withImageNameFilter(MONGO_IMAGE_IDENTIFIER)
                .exec()
                .stream()
                .findFirst()
                .map(Image::getId);
    }

    private void pullMongoImage() {
        final DockerImagePullHandler dockerImagePullHandler = DockerImagePullHandler.newInstance();
        dockerClient.pullImageCmd(MONGO_IMAGE_IDENTIFIER).exec(dockerImagePullHandler);
        dockerImagePullHandler.getImagePullFuture().join();
    }

}
