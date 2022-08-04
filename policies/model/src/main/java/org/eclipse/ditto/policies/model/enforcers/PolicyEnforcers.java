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
package org.eclipse.ditto.policies.model.enforcers;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.enforcers.tree.TreeBasedPolicyEnforcer;
import org.eclipse.ditto.policies.model.enforcers.trie.TrieBasedPolicyEnforcer;

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
     * @throws NullPointerException if {@code policyEntries} is {@code null}.
     */
    public static Enforcer defaultEvaluator(final Iterable<PolicyEntry> policyEntries) {
        return throughputOptimizedEvaluator(policyEntries);
    }

    /**
     * Returns an Enforcer which requires more memory (factor 2-4 more than {@link
     * #memoryOptimizedEvaluator(Iterable)}) but delivers very high throughput for most of the Policies, especially good
     * for complex Policies with multiple subjects.
     * <p>
     * Building JsonViews has also a higher throughput with this algorithm.
     *
     * @param policyEntries the Policy entries to initialize the evaluator with.
     * @return the initialized throughput optimized Enforcer.
     * @throws NullPointerException if {@code policyEntries} is {@code null}.
     */
    public static Enforcer throughputOptimizedEvaluator(final Iterable<PolicyEntry> policyEntries) {
        return TrieBasedPolicyEnforcer.newInstance(policyEntries);
    }

    /**
     * Returns an Enforcer which requires little memory and delivers good performance for most of the Policies.
     *
     * @param policyEntries the Policy entries to initialize the evaluator with.
     * @return the initialized memory optimized Enforcer.
     * @throws NullPointerException if {@code policyEntries} is {@code null}.
     */
    public static Enforcer memoryOptimizedEvaluator(final Iterable<PolicyEntry> policyEntries) {
        return TreeBasedPolicyEnforcer.createInstance(policyEntries);
    }

}
