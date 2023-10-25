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
package org.eclipse.ditto.internal.utils.test.docker.mongo;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

import org.eclipse.ditto.internal.utils.test.docker.ContainerFactory;

import com.github.dockerjava.api.command.CreateContainerCmd;

/**
 * Responsible for creating and configuring the mongo db docker container that should be started for tests.
 */
final class MongoContainerFactory extends ContainerFactory {

    private static final String MONGO_IMAGE_NAME = "mongo";
    private static final String DEFAULT_MONGO_VERSION = "4.2";
    private static final int MONGO_INTERNAL_PORT = 27017;
    private static final List<String> MONGO_COMMANDS = List.of("mongod", "--storageEngine", "wiredTiger");

    private static final MongoContainerFactory INSTANCE = new MongoContainerFactory(DEFAULT_MONGO_VERSION);

    private MongoContainerFactory(final String mongoVersion) {
        super(MONGO_IMAGE_NAME + ":" + mongoVersion, MONGO_INTERNAL_PORT);
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

    @Override
    protected CreateContainerCmd configureContainer(CreateContainerCmd createContainerCmd) {
        return createContainerCmd.withCmd(MONGO_COMMANDS);
    }

    @Override
    public void close() throws IOException {
        // never close the default instance
        if (this != INSTANCE) {
            super.close();
        }
    }
}
