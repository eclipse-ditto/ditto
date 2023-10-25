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
package org.eclipse.ditto.internal.utils.test.docker.mongo;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.internal.utils.test.docker.DockerResource;

/**
 * External Mongo DB resource for utilization within tests.
 */
@NotThreadSafe
public final class MongoDbResource extends DockerResource {

    private static final int MONGO_INTERNAL_PORT = 27017;

    public MongoDbResource() {
        super(MongoContainerFactory.getInstance());
    }

    public MongoDbResource(final String mongoVersion) {
        super(MongoContainerFactory.of(mongoVersion));
    }

    /**
     * @return the port on which the db listens.
     */
    public int getPort() {
        return super.getPort(MONGO_INTERNAL_PORT);
    }

}
