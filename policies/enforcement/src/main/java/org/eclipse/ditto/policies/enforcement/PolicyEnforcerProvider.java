/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Provides instances of {@link PolicyEnforcer} based on a given {@link PolicyId}.
 * Implementations may choose to apply caching.
 */
@FunctionalInterface
public interface PolicyEnforcerProvider {

    /**
     * The configuration key used to configure the policy enforcer cache (e.g. its size).
     */
    String ENFORCER_CACHE_CONFIG_KEY = "ditto.policies-enforcer-cache";

    /**
     * Tries to retrieve a {@link PolicyEnforcer} for the passed {@code policyId}.
     *
     * @param policyId the Policy ID to retrieve the PolicyEnforcer for.
     * @return a CompletionStage of an optionally retrieved {@code PolicyEnforcer} or an empty optional if it could not
     * be retrieved.
     */
    CompletionStage<Optional<PolicyEnforcer>> getPolicyEnforcer(@Nullable PolicyId policyId);

}
