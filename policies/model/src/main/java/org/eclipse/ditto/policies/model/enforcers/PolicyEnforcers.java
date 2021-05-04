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

import org.eclipse.ditto.policies.model.enforcers.tree.TreeBasedPolicyEnforcer;
import org.eclipse.ditto.policies.model.enforcers.trie.TrieBasedPolicyEnforcer;
import org.eclipse.ditto.policies.model.Policy;

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
     * @param policy the Policy to initialize the evaluator with.
     * @return the initialized general purpose Enforcer.
     * @throws NullPointerException if {@code policy} is {@code null}.
     */
    public static Enforcer defaultEvaluator(final Policy policy) {
        return throughputOptimizedEvaluator(policy);
    }

    /**
     * Returns a Enforcer which requires more memory (factor 2-4 more than {@link
     * #memoryOptimizedEvaluator(org.eclipse.ditto.policies.model.Policy)}) but delivers very high throughput for most of the Policies, especially good
     * for complex Policies with multiple subjects.
     * <p>
     * Building JsonViews has also a higher throughput with this algorithm.
     *
     * @param policy the Policy to initialize the evaluator with.
     * @return the initialized throughput optimized Enforcer.
     * @throws NullPointerException if {@code policy} is {@code null}.
     */
    public static Enforcer throughputOptimizedEvaluator(final Policy policy) {
        return TrieBasedPolicyEnforcer.newInstance(policy);
    }

    /**
     * Returns a Enforcer which requires little memory and delivers good performance for most of the Policies.
     *
     * @param policy the Policy to initialize the evaluator with.
     * @return the initialized memory optimized Enforcer.
     * @throws NullPointerException if {@code policy} is {@code null}.
     */
    public static Enforcer memoryOptimizedEvaluator(final Policy policy) {
        return TreeBasedPolicyEnforcer.createInstance(policy);
    }

}
