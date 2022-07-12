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
package org.eclipse.ditto.internal.utils.test.mongo;

import java.io.Closeable;
import java.io.IOException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.junit.rules.ExternalResource;

/**
 * External Mongo DB resource for utilization within tests.
 * If the environment variable "HOSTING_ENVIRONMENT" is set to "IDE", then the resource will use localhost:27017 as
 * the MongoDB container address instead of starting its own container.
 */
@NotThreadSafe
public final class MongoDbResource extends ExternalResource {

    private static final int MONGO_INTERNAL_PORT = 27017;
    private static final String MONGO_CONTAINER_ALREADY_STARTED = "Mongo container has already been started.";
    private static final String HOSTING_ENVIRONMENT_ENV_VARIABLE_NAME = "HOSTING_ENVIRONMENT";
    private static final String IDE_HOSTING_ENVIRONMENT = "IDE";

    private final MongoContainerFactory mongoContainerFactory;
    @Nullable private DockerContainer mongoContainer;

    public MongoDbResource() {
        this.mongoContainerFactory = MongoContainerFactory.getInstance();
    }

    public MongoDbResource(final String mongoVersion) {
        this.mongoContainerFactory = MongoContainerFactory.of(mongoVersion);
    }

    @Override
    protected void before() {
        if (mongoContainer != null) {
            throw new IllegalStateException(MONGO_CONTAINER_ALREADY_STARTED);
        }
        if (!IDE_HOSTING_ENVIRONMENT.equals(System.getenv(HOSTING_ENVIRONMENT_ENV_VARIABLE_NAME))) {
            mongoContainer = mongoContainerFactory.createMongoContainer();
            mongoContainer.start();
        }
    }

    @Override
    protected void after() {
        if (mongoContainer != null) {
            mongoContainer.stop();
            mongoContainer.remove();
            mongoContainer = null;
        }
        try {
            mongoContainerFactory.close();
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * @return the port on which the db listens.
     */
    public int getPort() {
        if (mongoContainer == null) {
            return MONGO_INTERNAL_PORT;
        }
        return mongoContainer.getPort(MONGO_INTERNAL_PORT);
    }

    /**
     * @return the IP on which the db was bound.
     */
    public String getBindIp() {
        if (mongoContainer == null) {
            return "localhost";
        }
        return mongoContainer.getHostname();
    }

}
