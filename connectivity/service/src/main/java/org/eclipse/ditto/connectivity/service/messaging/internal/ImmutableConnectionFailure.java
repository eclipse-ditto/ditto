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

import java.time.Instant;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;

import akka.actor.ActorRef;
import akka.actor.Status;

/**
 * Immutable implementation of {@link ConnectionFailure}.
 */
@Immutable
public final class ImmutableConnectionFailure extends AbstractWithOrigin implements ConnectionFailure {

    @Nullable private final Throwable cause;
    @Nullable private final String description;
    private final ConnectivityStatus connectivityStatus;
    private final Instant time;

    private ImmutableConnectionFailure(@Nullable final ActorRef origin, @Nullable final Throwable cause,
            @Nullable final String description, final ConnectivityStatus connectivityStatus) {
        super(origin);
        this.cause = cause;
        this.description = description;
        time = Instant.now();
        this.connectivityStatus = connectivityStatus;
    }

    /**
     * Constructs a new ImmutableConnectionFailure which was most likely cause by an internal problem.
     *
     * @param origin the origin ActorRef
     * @param cause the cause of the Failure
     * @param description an optional description
     */
    public static ImmutableConnectionFailure internal(@Nullable final ActorRef origin, @Nullable final Throwable cause,
            @Nullable final String description) {
        return new ImmutableConnectionFailure(origin, cause, description, ConnectivityStatus.FAILED);
    }


    /**
     * Constructs a new ImmutableConnectionFailure which was most likely caused by an issue outside of the service.
     * This could be for example a misconfiguration of the connection by a user or a temporary downtime of the broker
     * or anything else that is not in our responsibility.
     *
     * @param origin the origin ActorRef
     * @param cause the cause of the Failure
     * @param description an optional description
     */
    public static ImmutableConnectionFailure userRelated(@Nullable final ActorRef origin, @Nullable final Throwable cause,
            @Nullable final String description) {
        return new ImmutableConnectionFailure(origin, cause, description, ConnectivityStatus.MISCONFIGURED);
    }

    @Override
    public Status.Failure getFailure() {
        return new Status.Failure(cause);
    }

    @Override
    public String getFailureDescription() {
        String responseStr = "";
        if (cause != null) {
            if (description != null) {
                responseStr = description + " - cause ";
            }
            responseStr += cause.getClass().getSimpleName() + ": " + cause.getMessage();
            if (cause instanceof DittoRuntimeException) {
                responseStr += " / " + ((DittoRuntimeException) cause).getDescription().orElse("");
            }
        } else if (description != null) {
            responseStr = description;
        } else {
            responseStr = "unknown failure";
        }
        responseStr += " at " + time;
        return responseStr;
    }

    @Override
    public ConnectivityStatus getStatus() {
        return connectivityStatus;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableConnectionFailure)) {
            return false;
        }
        final ImmutableConnectionFailure that = (ImmutableConnectionFailure) o;
        return Objects.equals(cause, that.cause) &&
                Objects.equals(description, that.description) &&
                Objects.equals(time, that.time);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cause, description, time);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", cause=" + cause +
                ", description=" + description +
                ", time=" + time +
                "]";
    }
}
