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
package org.eclipse.ditto.services.models.things;

import javax.annotation.concurrent.Immutable;

/**
 * Constants for the Things messaging.
 */
@Immutable
public final class ThingsMessagingConstants {

    @SuppressWarnings("squid:S1075")
    private static final String USER_PATH = "/user";

    /**
     * Path of the root actor.
     */
    public static final String ROOT_ACTOR_PATH = USER_PATH + "/thingsRoot";

    /**
     * Path of the things-stream-provider actor.
     */
    public static final String THINGS_STREAM_PROVIDER_ACTOR_PATH = ROOT_ACTOR_PATH + "/persistenceStreamingActor";

    /**
     * Name of the shard region for Thing entities.
     */
    public static final String SHARD_REGION = "thing";

    /**
     * Name of the akka cluster role.
     */
    public static final String CLUSTER_ROLE = "things";

    /*
     * Inhibit instantiation of this utility class.
     */
    private ThingsMessagingConstants() {
        // no-op
    }
}
