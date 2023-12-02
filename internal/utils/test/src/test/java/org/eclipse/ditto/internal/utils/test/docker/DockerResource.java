/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import java.io.IOException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.junit.rules.ExternalResource;

/**
 * External docker resource for utilization within tests.
 * If the environment variable "HOSTING_ENVIRONMENT" is set to "IDE", then the resource will use localhost:{PORT}
 * instead of starting its own container.
 */
@NotThreadSafe
public abstract class DockerResource extends ExternalResource {

    private static final String CONTAINER_ALREADY_STARTED = "Container has already been started.";
    private static final String HOSTING_ENVIRONMENT_ENV_VARIABLE_NAME = "HOSTING_ENVIRONMENT";
    private static final String IDE_HOSTING_ENVIRONMENT = "IDE";

    private final ContainerFactory containerFactory;
    @Nullable private DockerContainer container;

    public DockerResource(ContainerFactory containerFactory) {
        this.containerFactory = containerFactory;
    }

    @Override
    protected void before() {
        if (container != null) {
            throw new IllegalStateException(CONTAINER_ALREADY_STARTED);
        }
        if (!IDE_HOSTING_ENVIRONMENT.equals(System.getenv(HOSTING_ENVIRONMENT_ENV_VARIABLE_NAME))) {
            container = containerFactory.createContainer();
            container.start();
        }
    }

    @Override
    protected void after() {
        if (container != null) {
            container.stop();
            container.remove();
            container = null;
        }
        try {
            containerFactory.close();
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * @return the port on which the container listens.
     */
    protected int getPort(final int port) {
        if (container == null) {
            return port;
        }
        return container.getPort(port);
    }

    /**
     * @return the IP on which the container was bound.
     */
    public String getBindIp() {
        if (container == null) {
            return "localhost";
        }
        return container.getHostname();
    }

}
