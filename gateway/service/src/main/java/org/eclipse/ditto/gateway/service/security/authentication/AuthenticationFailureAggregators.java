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
package org.eclipse.ditto.gateway.service.security.authentication;

import javax.annotation.concurrent.Immutable;

/**
 * Provides instances of {@link AuthenticationFailureAggregator}.
 */
@Immutable
public final class AuthenticationFailureAggregators {

    private AuthenticationFailureAggregators() {
        throw new AssertionError();
    }

    /**
     * Returns an instance of the default authentication aggregator.
     *
     * @return the instance.
     */
    public static AuthenticationFailureAggregator getDefault() {
        return DefaultAuthenticationFailureAggregator.getInstance();
    }

}
