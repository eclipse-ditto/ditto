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
package org.eclipse.ditto.services.models.amqpbridge;

import javax.annotation.concurrent.Immutable;

/**
 * Constants for the AMQP Bridge messaging.
 */
@Immutable
public final class AmqpBridgeMessagingConstants {

    /**
     * Name of the shard region for AMQP connections.
     */
    public static final String SHARD_REGION = "amqp-connection";

    /**
     * Name of the akka cluster role.
     */
    public static final String CLUSTER_ROLE = "amqp-bridge";

    /*
     * Inhibit instantiation of this utility class.
     */
    private AmqpBridgeMessagingConstants() {
        // no-op
    }
}
