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
package org.eclipse.ditto.model.enforcers;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.enforcers.tree.TreeBasedPolicyEnforcer;
import org.eclipse.ditto.model.enforcers.trie.TrieBasedPolicyEnforcer;
import org.eclipse.ditto.model.policies.PolicyEntry;

/**
 * Contains multiple implementations of {@link Enforcer}s suited for different use cases.
 */
@Immutable
public final class PolicyEnforcers {

    private PolicyEnforcers() {
        throw new AssertionError();
    }

    /**
     * Returns a general purpose Enforcer which requires some memory and delivers very high throughput for
     * most of the Policies.
     *
     * @param policyEntries the Policy entries to initialize the evaluator with.
     * @return the initialized general purpose Enforcer.
     * @throws NullPointerException if {@code policy} is {@code null}.
     */
    public static Enforcer defaultEvaluator(final Iterable<PolicyEntry> policyEntries) {
        return throughputOptimizedEvaluator(policyEntries);
    }

    /**
     * Returns a Enforcer which requires more memory (factor 2-4 more than {@link
     * #memoryOptimizedEvaluator(Iterable)}) but delivers very high throughput for most of the Policies, especially good
     * for complex Policies with multiple subjects.
     * <p>
     * Building JsonViews has also a higher throughput with this algorithm.
     *
     * @param policyEntries the Policy entries to initialize the evaluator with.
     * @return the initialized throughput optimized Enforcer.
     * @throws NullPointerException if {@code policy} is {@code null}.
     */
    public static Enforcer throughputOptimizedEvaluator(final Iterable<PolicyEntry> policyEntries) {
        return TrieBasedPolicyEnforcer.newInstance(policyEntries);
    }

    /**
     * Returns a Enforcer which requires little memory and delivers good performance for most of the Policies.
     *
     * @param policyEntries the Policy entries to initialize the evaluator with.
     * @return the initialized memory optimized Enforcer.
     * @throws NullPointerException if {@code policy} is {@code null}.
     */
    public static Enforcer memoryOptimizedEvaluator(final Iterable<PolicyEntry> policyEntries) {
        return TreeBasedPolicyEnforcer.createInstance(policyEntries);
    }

}
