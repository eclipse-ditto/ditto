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

import java.util.List;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;

/**
 * Responsible for aggregating the reasons for failure of multiple failed
 * {@link AuthenticationResult authentication results} to a single {@link DittoRuntimeException}.
 */
@FunctionalInterface
public interface AuthenticationFailureAggregator {

    /**
     * Aggregates reasons for failure of the given failed authentication results to a single {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException ditto runtime exception}.
     *
     * @param failedAuthenticationResults the list of failed authentication results to aggregate. Must not be empty.
     * @return the exception with the aggregated failure information.
     * @throws java.lang.IllegalArgumentException if the given list of failed authentication results is empty.
     */
    DittoRuntimeException aggregateAuthenticationFailures(List<AuthenticationResult> failedAuthenticationResults);

    static AuthenticationFailureAggregator getInstance() {
        return DefaultAuthenticationFailureAggregator.getInstance();
    }
}
