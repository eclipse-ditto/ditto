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

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.junit.rules.ExternalResource;

/**
 * External Mongo DB resource for utilization within tests.
 */
@NotThreadSafe
public final class MongoDbResource extends ExternalResource {

    private static final int MONGO_INTERNAL_PORT = 27017;
    private static final String MONGO_CONTAINER_NOT_STARTED = "Mongo container hast not been started, yet";
    private static final String MONGO_CONTAINER_ALREADY_STARTED = "Mongo container has already been started.";

    @Nullable private DockerContainer mongoContainer;

    @Override
    protected void before() {
        if (mongoContainer != null) {
            throw new IllegalStateException(MONGO_CONTAINER_ALREADY_STARTED);
        }
        mongoContainer = MongoContainerFactory.getInstance().createMongoContainer();
        mongoContainer.start();
    }

    @Override
    protected void after() {
        if (mongoContainer == null) {
            throw new IllegalStateException(MONGO_CONTAINER_NOT_STARTED);
        }
        mongoContainer.stop();
        mongoContainer.remove();
        mongoContainer = null;
    }

    /**
     * @return the port on which the db listens.
     */
    public int getPort() {
        if (mongoContainer == null) {
            throw new IllegalStateException(MONGO_CONTAINER_NOT_STARTED);
        }
        return mongoContainer.getPort(MONGO_INTERNAL_PORT);
    }

    /**
     * @return the IP on which the db was bound.
     */
    public String getBindIp() {
        if (mongoContainer == null) {
            throw new IllegalStateException(MONGO_CONTAINER_NOT_STARTED);
        }
        return mongoContainer.getHostname();
    }

}
