/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;

/**
 * Resolves the correct {@link ConnectivityStatus} from a given {@link ConnectionFailure}.
 */
public final class ConnectivityStatusResolver {

    private final UserIndicatedErrors userIndicatedErrors;

    private ConnectivityStatusResolver(final UserIndicatedErrors userIndicatedErrors) {
        this.userIndicatedErrors = userIndicatedErrors;
    }

    /**
     * Creates a new instance of {@link ConnectivityStatusResolver}.
     *
     * @param userIndicatedErrors the list of errors that should be treated as user indicated errors.
     * @return the new status resolver.
     */
    static ConnectivityStatusResolver of(final UserIndicatedErrors userIndicatedErrors) {
        return new ConnectivityStatusResolver(userIndicatedErrors);
    }

    /**
     * Resolves the correct {@link ConnectivityStatus} from a given {@link ConnectionFailure}.
     *
     * @param connectionFailure the failure.
     * @return the resolved status.
     */
    public ConnectivityStatus resolve(final ConnectionFailure connectionFailure) {
        return connectionFailure.getStatus()
                .orElseGet(() -> this.resolve(connectionFailure.getFailure().cause()));
    }

    /**
     * Resolves the correct {@link ConnectivityStatus} from a given {@link Throwable}.
     *
     * @param throwable the throwable.
     * @return the resolved status.
     */
    public ConnectivityStatus resolve(@Nullable final Throwable throwable) {
        return userIndicatedErrors.matches(throwable) ?
                ConnectivityStatus.MISCONFIGURED :
                ConnectivityStatus.FAILED;
    }

}
