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

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.signals.FeatureToggle;
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
        final Iterable<PolicyEntry> effectiveEntries = effectiveEntries(policyEntries);
        if (FeatureToggle.isPolicyEnforcementUseThroughputOptimizedEvaluatorEnabled()) {
            return throughputOptimizedEvaluator(effectiveEntries);
        } else {
            return memoryOptimizedEvaluator(effectiveEntries);
        }
    }

    /**
     * Same as {@link #defaultEvaluator(Iterable)} but bounds the memory-optimized enforcer's authorization-verdict
     * memos at {@code maxMemoSize} entries (a value {@code <= 0} disables memoization; see
     * {@link TreeBasedPolicyEnforcer#createInstance(Iterable, int)}). The bound only applies to the memory-optimized
     * evaluator; the throughput-optimized (trie-based) evaluator does not memoize and ignores it.
     *
     * @param policyEntries the Policy entries to initialize the evaluator with.
     * @param maxMemoSize the per-memo best-effort upper bound; {@code <= 0} disables memoization.
     * @return the initialized general purpose Enforcer.
     * @throws NullPointerException if {@code policyEntries} is {@code null}.
     */
    public static Enforcer defaultEvaluator(final Iterable<PolicyEntry> policyEntries, final int maxMemoSize) {
        final Iterable<PolicyEntry> effectiveEntries = effectiveEntries(policyEntries);
        if (FeatureToggle.isPolicyEnforcementUseThroughputOptimizedEvaluatorEnabled()) {
            return throughputOptimizedEvaluator(effectiveEntries);
        } else {
            return memoryOptimizedEvaluator(effectiveEntries, maxMemoSize);
        }
    }

    // Skip entries that cannot contribute to access decisions: entries without subjects can't authorize anyone,
    // entries without resources can't grant or revoke anything. Applied after import/reference resolution.
    private static Iterable<PolicyEntry> effectiveEntries(final Iterable<PolicyEntry> policyEntries) {
        return StreamSupport
                .stream(policyEntries.spliterator(), false)
                .filter(entry -> !entry.getSubjects().isEmpty() && !entry.getResources().isEmpty())
                .collect(Collectors.toList());
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

    /**
     * Same as {@link #memoryOptimizedEvaluator(Iterable)} but bounds each authorization-verdict memo at
     * {@code maxMemoSize} entries; a value {@code <= 0} disables memoization entirely (no maps are allocated).
     *
     * @param policyEntries the Policy entries to initialize the evaluator with.
     * @param maxMemoSize the per-memo best-effort upper bound; {@code <= 0} disables memoization.
     * @return the initialized memory optimized Enforcer.
     * @throws NullPointerException if {@code policyEntries} is {@code null}.
     */
    public static Enforcer memoryOptimizedEvaluator(final Iterable<PolicyEntry> policyEntries, final int maxMemoSize) {
        return TreeBasedPolicyEnforcer.createInstance(policyEntries, maxMemoSize);
    }

}
