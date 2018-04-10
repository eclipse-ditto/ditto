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
package org.eclipse.ditto.services.models.concierge;

import javax.annotation.concurrent.Immutable;

/**
 * Constants for the concierge service.
 */
@Immutable
public final class ConciergeMessagingConstants {

    /**
     * Path of the concierge dispatcher actor.
     */
    public static final String DISPATCHER_ACTOR_PATH = "/user/conciergeRoot/dispatcherActor";

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
