/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.policies;

import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.id.DefaultNamespacedEntityId;
import org.eclipse.ditto.model.base.entity.id.NamespacedEntityId;
import org.eclipse.ditto.model.base.entity.id.NamespacedEntityIdInvalidException;
import org.eclipse.ditto.model.base.entity.id.NamespacedEntityIdWithType;
import org.eclipse.ditto.model.base.entity.type.EntityType;

/**
 * Java representation of a policy ID.
 */
@Immutable
public final class PolicyId extends NamespacedEntityIdWithType {

    private PolicyId(final NamespacedEntityId entityId) {
        super(entityId);
    }

    /**
     * Returns a {@link PolicyId} based on the given policyId CharSequence. May return the same instance as
     * the parameter if the given parameter is already a PolicyId. Skips validation if the given
     * {@code policyId} is an instance of NamespacedEntityId.
     *
     * @param policyId The policy ID.
     * @return the policy ID.
     */
    public static PolicyId of(final CharSequence policyId) {

        if (policyId instanceof PolicyId) {
            return (PolicyId) policyId;
        }

        return wrapInPolicyIdInvalidException(() -> new PolicyId(DefaultNamespacedEntityId.of(policyId)));
    }

    public static PolicyId of(final PolicyId policyId) {
        return policyId;
    }

    /**
     * Creates a new {@link PolicyId} with the given namespace and name.
     *
     * @param namespace the namespace of the policy.
     * @param policyName the name of the policy.
     * @return the created instance of {@link PolicyId}
     */
    public static PolicyId of(final String namespace, final String policyName) {
        return wrapInPolicyIdInvalidException(() -> new PolicyId(DefaultNamespacedEntityId.of(namespace, policyName)));
    }

    /**
     * Generates a policy ID with a random unique name inside the given namespace.
     *
     * @param namespace the namespace of the policy.
     * @return The generated unique policy ID.
     */
    public static PolicyId inNamespaceWithRandomName(final String namespace) {
        return of(namespace, UUID.randomUUID().toString());
    }

    private static <T> T wrapInPolicyIdInvalidException(final Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (final NamespacedEntityIdInvalidException e) {
            throw PolicyIdInvalidException.newBuilder(e.getEntityId().orElse(null)).cause(e).build();
        }
    }

    @Override
    public EntityType getEntityType() {
        return PolicyConstants.ENTITY_TYPE;
    }
}
