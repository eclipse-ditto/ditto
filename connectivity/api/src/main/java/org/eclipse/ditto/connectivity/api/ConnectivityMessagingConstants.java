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
package org.eclipse.ditto.connectivity.api;

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

    public static final String CONNECTION_ID_RETRIEVAL_ACTOR_NAME = "connectionIdsRetrieval";

    public static final String CONNECTION_ID_RETRIEVAL_ACTOR_PATH =
            "/user/connectivityRoot/" + CONNECTION_ID_RETRIEVAL_ACTOR_NAME;

    /*
     * Inhibit instantiation of this utility class.
     */
    private ConnectivityMessagingConstants() {
        // no-op
    }
}
