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
package org.eclipse.ditto.services.utils.test.mongo;

import org.junit.rules.ExternalResource;

/**
 * External Mongo DB resource for utilization within tests.
 */
public final class MongoDbResource extends ExternalResource {

    private static final int MONGO_INTERNAL_PORT = 27017;
    private final DockerContainer mongoContainer;

    public MongoDbResource() {
        mongoContainer = MongoContainerFactory.getInstance().createMongoContainer();
    }

    @Override
    protected void before() {
        mongoContainer.start();
    }

    @Override
    protected void after() {
        mongoContainer.stop();
        mongoContainer.remove();
    }

    /**
     * @return the port on which the db listens.
     */
    public int getPort() {
        return mongoContainer.getPort(MONGO_INTERNAL_PORT);
    }

    /**
     * @return the IP on which the db was bound.
     */
    public String getBindIp() {
        return mongoContainer.getHostname();
    }

}
