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

import java.util.List;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;

/**
 * Responsible for aggregating the reasons for failure of multiple failed {@link AuthenticationResult}s to a single
 * {@link DittoRuntimeException}.
 */
@FunctionalInterface
public interface AuthenticationFailureAggregator {

    /**
     * Aggregates reasons for failure of the given failed authentication results to a single
     * {@link DittoRuntimeException}.
     *
     * @param failedAuthenticationResults the list of failed authentication results to aggregate.
     * Must not be empty!
     * Reasons of failure that are no DittoRuntimeExceptions and do not have a Cause of DittoRuntimeException will be
     * ignored.
     * Reasons of failure that does not contain a description will be ignored.
     * @return the exception with the aggregated failure information.
     * @throws IllegalArgumentException if the given list of failed authentication results is either empty or
     * did not contain any failure reason of type DittoRuntimeException containing a description.
     */
    DittoRuntimeException aggregateAuthenticationFailures(List<AuthenticationResult> failedAuthenticationResults);

}
