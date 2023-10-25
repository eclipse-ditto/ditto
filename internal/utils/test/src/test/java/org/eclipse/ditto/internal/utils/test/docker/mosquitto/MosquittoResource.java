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
package org.eclipse.ditto.internal.utils.test.docker.mosquitto;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.internal.utils.test.docker.DockerResource;

/**
 * External Mosquitto resource for utilization within tests.
 */
@NotThreadSafe
public final class MosquittoResource extends DockerResource {

    private static final int MOSQUITTO_INTERNAL_PORT = 1883;

    public MosquittoResource(final String configResourceName) {
        super(MosquittoContainerFactory.of(configResourceName));
    }

    /**
     * @return the port on which the mosquitto listens.
     */
    public int getPort() {
        return super.getPort(MOSQUITTO_INTERNAL_PORT);
    }

}
