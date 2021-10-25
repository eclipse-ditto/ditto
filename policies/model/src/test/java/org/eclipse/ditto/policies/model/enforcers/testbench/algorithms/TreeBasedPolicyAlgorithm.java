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
package org.eclipse.ditto.policies.model.enforcers.testbench.algorithms;

import java.util.Set;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.policies.model.enforcers.EffectedSubjects;
import org.eclipse.ditto.policies.model.enforcers.tree.TreeBasedPolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.ResourceKey;

public final class TreeBasedPolicyAlgorithm implements PolicyAlgorithm {

    private final TreeBasedPolicyEnforcer treeBasedPolicyEvaluator;

    public TreeBasedPolicyAlgorithm(final Policy policy) {
        treeBasedPolicyEvaluator = TreeBasedPolicyEnforcer.createInstance(policy);
    }

    @Override
    public boolean hasUnrestrictedPermissions(final ResourceKey resourceKey,
            final AuthorizationContext authorizationContext,
            final Permissions permissions) {
        return treeBasedPolicyEvaluator.hasUnrestrictedPermissions(resourceKey, authorizationContext, permissions);
    }

    @Override
    public EffectedSubjects getSubjectsWithPermission(final ResourceKey resourceKey, final Permissions permissions) {
        return treeBasedPolicyEvaluator.getSubjectsWithPermission(resourceKey, permissions);
    }

    @Override
    public Set<AuthorizationSubject> getSubjectsWithPartialPermission(final ResourceKey resourceKey,
            final Permissions permissions) {

        return treeBasedPolicyEvaluator.getSubjectsWithPartialPermission(resourceKey, permissions);
    }

    @Override
    public boolean hasPartialPermissions(final ResourceKey resourceKey,
            final AuthorizationContext authorizationContext, final Permissions permissions) {

        return treeBasedPolicyEvaluator.hasPartialPermissions(resourceKey, authorizationContext, permissions);
    }

    @Override
    public Set<AuthorizationSubject> getSubjectsWithUnrestrictedPermission(final ResourceKey resourceKey,
            final Permissions permissions) {
        return treeBasedPolicyEvaluator.getSubjectsWithUnrestrictedPermission(resourceKey, permissions);
    }

    @Override
    public JsonObject buildJsonView(final ResourceKey resourceKey, final Iterable<JsonField> jsonFields,
            final AuthorizationContext authorizationContext, final Permissions permissions) {
        return treeBasedPolicyEvaluator.buildJsonView(resourceKey, jsonFields, authorizationContext, permissions);
    }

}
