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
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.model.ConnectivityStatus;

import akka.actor.ActorRef;
import akka.actor.Status;

/**
 * Immutable implementation of {@link ConnectionFailure}.
 */
@Immutable
final class ImmutableConnectionFailure extends AbstractWithOrigin implements ConnectionFailure {

    @Nullable private final Throwable cause;
    @Nullable private final String description;
    @Nullable private final ConnectivityStatus connectivityStatus;
    private final Instant time;

    ImmutableConnectionFailure(@Nullable final ActorRef origin, @Nullable final Throwable cause,
            @Nullable final String description, @Nullable final ConnectivityStatus connectivityStatus) {
        super(origin);
        this.cause = cause;
        this.description = description;
        time = Instant.now();
        this.connectivityStatus = connectivityStatus;
    }

    @Override
    public Status.Failure getFailure() {
        return new Status.Failure(cause);
    }

    @Override
    public String getFailureDescription() {
        return ConnectionFailure.determineFailureDescription(time, cause, description);
    }

    @Override
    public Optional<ConnectivityStatus> getStatus() {
        return Optional.ofNullable(connectivityStatus);
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
                Objects.equals(time, that.time) &&
                Objects.equals(connectivityStatus, that.connectivityStatus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cause, description, time, connectivityStatus);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", cause=" + throwableToString(cause) +
                ", description=" + description +
                ", time=" + time +
                ", connectivityStatus=" + connectivityStatus +
                "]";
    }

    private static String throwableToString(@Nullable final Throwable cause) {
        if (null == cause) {
            return "null";
        }
        final String tDescription = ConnectionFailure.determineFailureDescription(null, cause, null);
        return null == cause.getCause() ?
                tDescription :
                tDescription + (" Cause: " + throwableToString(cause.getCause()));
    }
}
