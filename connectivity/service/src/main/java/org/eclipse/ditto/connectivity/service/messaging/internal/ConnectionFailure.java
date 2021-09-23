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

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
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
        return new ImmutableConnectionFailure(origin, getRealCause(cause), description, null);
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
        return new ImmutableConnectionFailure(origin, getRealCause(cause), description, ConnectivityStatus.FAILED);
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
        return new ImmutableConnectionFailure(origin, getRealCause(cause), description,
                ConnectivityStatus.MISCONFIGURED);
    }

    /**
     * Determines a nicely formatted failure description string based on the based in optional {@code cause}, an
     * optional {@code description} and the {@code time}.
     *
     * @param time the optional time to include in the description.
     * @param cause the optional cause to extract {@code message} and (if it was a {@code DittoRuntimeException}
     * {@code description} from.
     * @param description an optional additional description to include in the created failure description.
     * @return the created nicely formatted failure description.
     */
    static String determineFailureDescription(@Nullable final Instant time,
            @Nullable final Throwable cause,
            @Nullable final String description) {
        String responseStr = "";
        if (cause != null) {
            if (description != null) {
                responseStr = description + " - cause ";
            }
            responseStr += String.format("<%s>: %s", cause.getClass().getSimpleName(), cause.getMessage());
            if (cause instanceof DittoRuntimeException) {
                if (!responseStr.endsWith(".")) {
                    responseStr += ".";
                }
                responseStr += ((DittoRuntimeException) cause).getDescription().map(d -> " " + d).orElse("");
            }
        } else {
            responseStr = Objects.requireNonNullElse(description, "unknown failure");
        }
        if (!responseStr.endsWith(".")) {
            responseStr += ".";
        }
        if (null != time) {
            responseStr += " At " + time;
        }
        return responseStr;
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

    @Nullable
    private static Throwable getRealCause(@Nullable final Throwable cause) {
        final Throwable realCause;
        if (cause instanceof CompletionException) {
            realCause = cause.getCause();
        } else if (cause instanceof ExecutionException) {
            realCause = cause.getCause();
        } else if (cause != null && IOException.class.equals(cause.getClass()) && cause.getMessage() == null &&
                cause.getCause() != null) {
            realCause = cause.getCause();
        } else {
            realCause = cause;
        }
        return realCause;
    }
}
