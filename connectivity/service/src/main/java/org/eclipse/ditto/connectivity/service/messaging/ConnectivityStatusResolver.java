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

import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;

/**
 * Resolves the correct {@link ConnectivityStatus} from a given {@link ConnectionFailure}.
 */
final class ConnectivityStatusResolver {

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
    ConnectivityStatus resolve(final ConnectionFailure connectionFailure) {
        return connectionFailure.getStatus()
                .orElseGet(() -> userIndicatedErrors.matches(connectionFailure.getFailure().cause()) ?
                        ConnectivityStatus.MISCONFIGURED :
                        ConnectivityStatus.FAILED);
    }

}
