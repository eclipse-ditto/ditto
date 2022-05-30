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
package org.eclipse.ditto.policies.model;

import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.AbstractNamespacedEntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityIdInvalidException;
import org.eclipse.ditto.base.model.entity.id.TypedEntityId;

/**
 * Java representation of a policy ID.
 */
@Immutable
@TypedEntityId(type = "policy")
public final class PolicyId extends AbstractNamespacedEntityId {

    /**
     * Will be resolved to the actual default namespace inside ditto.
     */
    private static final String DEFAULT_NAMESPACE = "";

    private PolicyId(final String namespace, final String policyName, final boolean shouldValidate) {
        super(PolicyConstants.ENTITY_TYPE, namespace, policyName, shouldValidate);
    }

    private PolicyId(final CharSequence policyId) {
        super(PolicyConstants.ENTITY_TYPE, policyId);
    }

    /**
     * Returns a {@code PolicyId} based on the given policyId CharSequence.
     * May return the same instance as the parameter if the given parameter is already a PolicyId.
     * Skips validation if the given {@code policyId} is an instance of NamespacedEntityId.
     *
     * @param policyId The policy ID.
     * @return the policy ID.
     */
    public static PolicyId of(final CharSequence policyId) {
        if (policyId instanceof PolicyId) {
            return (PolicyId) policyId;
        }

        if (policyId instanceof NamespacedEntityId) {
            final NamespacedEntityId namespacedEntityId = (NamespacedEntityId) policyId;
            return new PolicyId(namespacedEntityId.getNamespace(), namespacedEntityId.getName(), false);
        }

        return wrapInPolicyIdInvalidException(() -> new PolicyId(policyId));
    }

    /**
     * Creates a new {@code PolicyId} with the given namespace and name.
     *
     * @param namespace the namespace of the policy.
     * @param policyName the name of the policy.
     * @return the created instance of {@code PolicyId}
     */
    public static PolicyId of(final String namespace, final String policyName) {
        return wrapInPolicyIdInvalidException(() -> new PolicyId(namespace, policyName, true));
    }

    /**
     * Generates a policy ID with a random unique name inside the given namespace.
     *
     * @param namespace the namespace of the policy.
     * @return The generated unique policy ID.
     * @throws PolicyIdInvalidException if for the given {@code namespace} a PolicyId cannot be derived.
     */
    public static PolicyId inNamespaceWithRandomName(final String namespace) {
        return of(namespace, UUID.randomUUID().toString());
    }

    /**
     * Returns an instance of this class with default namespace placeholder.
     *
     * @param name the name of the policy.
     * @return the created ID.
     * @throws PolicyIdInvalidException if for the given {@code name} a PolicyId cannot be derived.
     * @since 3.0.0
     */
    public static PolicyId inDefaultNamespace(final String name) {
        return wrapInPolicyIdInvalidException(() -> new PolicyId(DEFAULT_NAMESPACE, name, true));
    }

    /**
     * Generates a new policy ID with the default namespace placeholder and a unique name.
     *
     * @return the generated policy ID.
     * @since 3.0.0
     */
    public static PolicyId generateRandom() {
        return wrapInPolicyIdInvalidException(
                () -> new PolicyId(DEFAULT_NAMESPACE, UUID.randomUUID().toString(), true));
    }

    private static <T> T wrapInPolicyIdInvalidException(final Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (final NamespacedEntityIdInvalidException e) {
            throw PolicyIdInvalidException.newBuilder(e.getEntityId().orElse(null)).cause(e).build();
        }
    }

}
