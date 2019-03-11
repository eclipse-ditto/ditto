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
package org.eclipse.ditto.services.models.concierge;

import javax.annotation.concurrent.Immutable;

/**
 * Constants for the concierge service.
 */
@Immutable
public final class ConciergeMessagingConstants {

    private static final String USER_PATH = "/user";

    /**
     * Path of the concierge root actor.
     */
    @SuppressWarnings("squid:S1075")
    public static final String ROOT_ACTOR_PATH = USER_PATH + "/conciergeRoot";

    /**
     * Path of the concierge dispatcher actor.
     */
    public static final String DISPATCHER_ACTOR_PATH = ROOT_ACTOR_PATH + "/dispatcherActor";

    /**
     * Path of the concierge forwarder actor.
     */
    public static final String FORWARDER_ACTOR_PATH = ROOT_ACTOR_PATH + "/conciergeForwarder";

    /**
     * Name of the blocked-namespace-updater singleton.
     */
    public static final String BLOCKED_NAMESPACES_UPDATER_NAME = "blockedNamespacesUpdater";

    /**
     * Name of the shard region for authorization entities.
     */
    public static final String SHARD_REGION = "concierge";

    /**
     * Name of the akka cluster role.
     */
    public static final String CLUSTER_ROLE = "concierge";

    /*
     * Inhibit instantiation of this utility class.
     */
    private ConciergeMessagingConstants() {
        // no-op
    }
}
