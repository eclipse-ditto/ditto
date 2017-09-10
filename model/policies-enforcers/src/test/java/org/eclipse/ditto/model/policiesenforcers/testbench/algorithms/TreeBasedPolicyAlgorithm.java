/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.policiesenforcers.testbench.algorithms;

import java.util.Set;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policiesenforcers.EffectedSubjectIds;
import org.eclipse.ditto.model.policiesenforcers.tree.TreeBasedPolicyEnforcer;


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
    public EffectedSubjectIds getSubjectIdsWithPermission(final ResourceKey resourceKey,
            final Permissions permissions) {

        return treeBasedPolicyEvaluator.getSubjectIdsWithPermission(resourceKey, permissions);
    }

    @Override
    public Set<String> getSubjectIdsWithPartialPermission(final ResourceKey resourceKey,
            final Permissions permissions) {

        return treeBasedPolicyEvaluator.getSubjectIdsWithPartialPermission(resourceKey, permissions);
    }

    @Override
    public boolean hasPartialPermissions(final ResourceKey resourceKey,
            final AuthorizationContext authorizationContext, final Permissions permissions) {

        return treeBasedPolicyEvaluator.hasPartialPermissions(resourceKey, authorizationContext, permissions);
    }

    @Override
    public JsonObject buildJsonView(final ResourceKey resourceKey, final Iterable<JsonField> jsonFields,
            final AuthorizationContext authorizationContext, final Permissions permissions) {
        return treeBasedPolicyEvaluator.buildJsonView(resourceKey, jsonFields, authorizationContext, permissions);
    }

}
