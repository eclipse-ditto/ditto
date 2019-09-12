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
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import java.io.Serializable;
import java.util.List;

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.Target;

import akka.actor.Props;

/**
 * Factory for creating {@link org.eclipse.ditto.services.connectivity.messaging.kafka.KafkaPublisherActor}s.
 */
public interface KafkaPublisherActorFactory extends Serializable {

    /**
     * Get the name that should be used for the actor that is created with {@code props}.
     *
     * @return the name of the actor.
     */
    String getActorName();

    /**
     * Get the props of the publisher actor that should be created.
     *
     * @param connectionId the connection ID.
     * @param targets the targets to publish to.
     * @param factory the connection factory to use.
     * @param dryRun if the publisher actor should be started in dry-run mode.
     * @return the {@code Props} to create the publisher actor.
     */
    Props props(ConnectionId connectionId, List<Target> targets, KafkaConnectionFactory factory, boolean dryRun);

}
