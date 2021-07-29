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
package org.eclipse.ditto.connectivity.service.messaging.internal;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.ConnectivityStatus;

import akka.actor.ActorRef;
import akka.actor.Status;

/**
 * Messaging internal error message for when a Failure was detected on a Connection.
 */
public interface ConnectionFailure extends WithOrigin {

    /**
     * Creates a new ConnectionFailure of not yet known differentiation between {@link ConnectivityStatus#FAILED} and
     * {@link ConnectivityStatus#MISCONFIGURED}.
     *
     * @param origin the origin ActorRef
     * @param cause the cause of the Failure
     * @param description an optional description
     * @return the created ConnectionFailure
     */
    static ConnectionFailure of(@Nullable final ActorRef origin, @Nullable final Throwable cause,
            @Nullable final String description) {
        return new ImmutableConnectionFailure(origin, cause, description, null);
    }

    /**
     * Creates a new ConnectionFailure which was most likely cause by an internal problem.
     *
     * @param origin the origin ActorRef
     * @param cause the cause of the Failure
     * @param description an optional description
     * @return the created ConnectionFailure
     */
    static ConnectionFailure internal(@Nullable final ActorRef origin, @Nullable final Throwable cause,
            @Nullable final String description) {
        return new ImmutableConnectionFailure(origin, cause, description, ConnectivityStatus.FAILED);
    }


    /**
     * Creates a new ConnectionFailure which was most likely caused by an issue outside of the service.
     * This could be for example a misconfiguration of the connection by a user or a temporary downtime of the broker
     * or anything else that is not in our responsibility.
     *
     * @param origin the origin ActorRef
     * @param cause the cause of the Failure
     * @param description an optional description
     * @return the created ConnectionFailure
     */
    static ConnectionFailure userRelated(@Nullable final ActorRef origin,
            @Nullable final Throwable cause,
            @Nullable final String description) {
        return new ImmutableConnectionFailure(origin, cause, description, ConnectivityStatus.MISCONFIGURED);
    }

    /**
     * @return the description of the failure.
     */
    String getFailureDescription();

    /**
     * @return the optionally already certainly known connectivity status.
     */
    Optional<ConnectivityStatus> getStatus();

    /**
     * @return the Failure containing the cause.
     */
    Status.Failure getFailure();
}
