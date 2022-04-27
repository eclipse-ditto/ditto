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
package org.eclipse.ditto.concierge.api;

import javax.annotation.concurrent.Immutable;

/**
 * Constants for the concierge service.
 */
@Immutable
public final class ConciergeMessagingConstants {

    @SuppressWarnings("squid:S1075")
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
     * Path of the concierge enforcer actor.
     */
    public static final String ENFORCER_ACTOR_PATH = ROOT_ACTOR_PATH + "/enforcer";

    /**
     * Name of the blocked-namespace-updater singleton.
     */
    public static final String BLOCKED_NAMESPACES_UPDATER_NAME = "blockedNamespacesUpdater";

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
