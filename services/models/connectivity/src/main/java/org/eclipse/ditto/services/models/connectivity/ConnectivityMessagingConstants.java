/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.models.connectivity;

import javax.annotation.concurrent.Immutable;

/**
 * Constants for the Connectivity messaging.
 */
@Immutable
public final class ConnectivityMessagingConstants {

    /**
     * Name of the shard region for connections.
     */
    public static final String SHARD_REGION = "connection";

    /**
     * Name of the akka cluster role.
     */
    public static final String CLUSTER_ROLE = "connectivity";

    /**
     * Target actor path where incoming messages are forwarded to.
     */
    public static final String GATEWAY_PROXY_ACTOR_PATH = "/user/gatewayRoot/proxy";

    /*
     * Inhibit instantiation of this utility class.
     */
    private ConnectivityMessagingConstants() {
        // no-op
    }
}
