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

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
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
final class MongoContainerFactory implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoContainerFactory.class);
    private static final String MONGO_IMAGE_NAME = "mongo";
    private static final String DEFAULT_MONGO_VERSION = "4.2";
    private static final int MONGO_INTERNAL_PORT = 27017;
    private static final PortBinding MONGO_PORT_BINDING_TO_RANDOM_PORT =
            new PortBinding(Ports.Binding.empty(), ExposedPort.tcp(MONGO_INTERNAL_PORT));
    private static final List<String> MONGO_COMMANDS = List.of("mongod", "--storageEngine", "wiredTiger");

    private static final MongoContainerFactory INSTANCE = new MongoContainerFactory(DEFAULT_MONGO_VERSION);
    private static final String UNIX_DOCKER_HOST = "unix:///var/run/docker.sock";
    private static final String WINDOWS_DOCKER_HOST = "npipe:////./pipe/docker_engine";

    private final String mongoImageIdentifier;
    private final DockerClient dockerClient;

    private MongoContainerFactory(final String mongoVersion) {
        mongoImageIdentifier = MONGO_IMAGE_NAME + ":" + mongoVersion;
        final String dockerHost = OsDetector.isWindows() ? WINDOWS_DOCKER_HOST : UNIX_DOCKER_HOST;
        LOGGER.info("Connecting to docker daemon on <{}>.", dockerHost);
        final DefaultDockerClientConfig config =
                DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(dockerHost).build();
        final ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        dockerClient = DockerClientImpl.getInstance(config, httpClient);

        LOGGER.info("Checking if Mongo image <{}> needs to be pulled.", mongoImageIdentifier);
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

    static MongoContainerFactory of(final String mongoVersion) {
        return new MongoContainerFactory(mongoVersion);
    }

    /**
     * Creates the mongo docker container with all required configuration and returns it.
     * It's not started after it has been returned.
     *
     * @return the created {@link DockerContainer}.
     */
    DockerContainer createMongoContainer() {
        return getMongoImageId()
                .map(imageId -> {
                    LOGGER.info("Creating container based on image with ID <{}>.", imageId);
                    return dockerClient.createContainerCmd(imageId)
                            .withCmd(MONGO_COMMANDS)
                            .withHostConfig(
                                    HostConfig.newHostConfig().withPortBindings(MONGO_PORT_BINDING_TO_RANDOM_PORT))
                            .exec()
                            .getId();
                })
                .map(containerId -> new DockerContainer(dockerClient, containerId))
                .orElseThrow(
                        () -> new IllegalStateException("Could not create container because no image was present.")
                );
    }

    private boolean isMongoImageAbsent() {
        final Optional<String> mongoImageId = getMongoImageId();
        mongoImageId.ifPresentOrElse(imageId -> {
            LOGGER.info("Mongo image <{}> is already present with ID <{}>", mongoImageIdentifier, imageId);
        }, () -> {
            LOGGER.info("Mongo image <{}> is not present, yet.", mongoImageIdentifier);
        });
        return mongoImageId.isEmpty();
    }

    private Optional<String> getMongoImageId() {
        try {
            return Optional.ofNullable(dockerClient.inspectImageCmd(mongoImageIdentifier).exec().getId());
        } catch (final NotFoundException e) {
            return Optional.empty();
        }
    }

    private void pullMongoImage() {
        LOGGER.info("Pulling <{}>.", mongoImageIdentifier);
        final DockerImagePullHandler dockerImagePullHandler = DockerImagePullHandler.newInstance();
        dockerClient.pullImageCmd(mongoImageIdentifier).exec(dockerImagePullHandler);
        dockerImagePullHandler.getImagePullFuture().join();
    }

    @Override
    public void close() throws IOException {
        // never close the default instance
        if (this != INSTANCE) {
            dockerClient.close();
        }
    }
}
