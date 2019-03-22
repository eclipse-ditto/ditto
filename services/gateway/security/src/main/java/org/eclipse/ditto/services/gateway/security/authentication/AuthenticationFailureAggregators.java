/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.gateway.security.authentication;

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
