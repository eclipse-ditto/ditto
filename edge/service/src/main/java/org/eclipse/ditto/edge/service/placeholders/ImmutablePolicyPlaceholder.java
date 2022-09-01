/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.edge.service.placeholders;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Placeholder implementation that replaces {@code policy:id}, {@code policy:namespace} and {@code policy:name}.
 * The input value is a String and must be a valid Policy ID.
 */
@Immutable
final class ImmutablePolicyPlaceholder extends AbstractEntityIdPlaceholder<PolicyId> implements PolicyPlaceholder {

    /**
     * Singleton instance of the ImmutablePolicyPlaceholder.
     */
    static final ImmutablePolicyPlaceholder INSTANCE = new ImmutablePolicyPlaceholder();

    @Override
    public String getPrefix() {
        return "policy";
    }

    @Override
    public List<String> resolveValues(final EntityId policyId, final String placeholder) {
        argumentNotEmpty(placeholder, "placeholder");
        checkNotNull(policyId, "Policy ID");
        if (policyId instanceof PolicyId policyId1) {
            return doResolve(policyId1, placeholder);
        } else {
            return Collections.emptyList();
        }
    }
}
