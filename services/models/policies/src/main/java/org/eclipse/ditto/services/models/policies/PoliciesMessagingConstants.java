/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.models.policies;

import javax.annotation.concurrent.Immutable;

/**
 * Constants for the Policies messaging.
 */
@Immutable
public final class PoliciesMessagingConstants {

    @SuppressWarnings("squid:S1075")
    private static final String USER_PATH = "/user";

    /**
     * Path of the root actor.
     */
    public static final String ROOT_ACTOR_PATH = USER_PATH + "/policiesRoot";

    /**
     * Path of the policies-stream-provider actor.
     */
    public static final String POLICIES_STREAM_PROVIDER_ACTOR_PATH = ROOT_ACTOR_PATH + "/persistenceStreamingActor";

    /**
     * Name of the shard region for Policy entities.
     */
    public static final String SHARD_REGION = "policy";

    /**
     * Name of the akka cluster role.
     */
    public static final String CLUSTER_ROLE = "policies";

    /*
     * Inhibit instantiation of this utility class.
     */
    private PoliciesMessagingConstants() {
        // no-op
    }

}
