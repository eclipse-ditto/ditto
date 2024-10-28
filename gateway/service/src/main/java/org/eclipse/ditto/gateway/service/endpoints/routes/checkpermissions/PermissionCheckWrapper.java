/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions;

import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Wrapper class for handling permission checks along with the associated {@link org.eclipse.ditto.policies.model.PolicyId}.
 * <p>
 * This class wraps an {@link org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions.ImmutablePermissionCheck} and provides an additional field to hold
 * the {@link org.eclipse.ditto.policies.model.PolicyId} that is associated with the permission check. The {@link PermissionCheckWrapper}
 * allows for flexible management of both the permission check and its related policy.
 *
 * @since 3.7.0
 */
public record PermissionCheckWrapper(ImmutablePermissionCheck permissionCheck, PolicyId policyId) {

    /**
     * Retrieves the {@link org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions.ImmutablePermissionCheck} contained within this wrapper.
     *
     * @return the wrapped permission check.
     */
    @Override
    public ImmutablePermissionCheck permissionCheck() {
        return permissionCheck;
    }


    /**
     * Retrieves the {@link org.eclipse.ditto.policies.model.PolicyId} associated with this permission check.
     *
     * @return the associated policy ID, or {@code null} if not set.
     */
    @Override
    public PolicyId policyId() {
        return policyId;
    }
}
