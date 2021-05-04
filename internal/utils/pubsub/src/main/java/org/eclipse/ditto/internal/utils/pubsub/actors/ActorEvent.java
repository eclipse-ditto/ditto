/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub.actors;

/**
 * Internal events for cooperation between actors involved in the pubsub infrastructure.
 */
public enum ActorEvent {
    /**
     * Event when an actor vital to pubsub terminated, which should trigger recovery behavior in all other actors.
     */
    PUBSUB_TERMINATED,

    /**
     * Event when AckUpdater is not available, which should trigger retry behavior in Subscriber.
     */
    ACK_UPDATER_NOT_AVAILABLE,

    /**
     * Debug command to force SubSupervisor to terminate all children without state maintenance to simulate crashing.
     */
    DEBUG_KILL_CHILDREN
}
