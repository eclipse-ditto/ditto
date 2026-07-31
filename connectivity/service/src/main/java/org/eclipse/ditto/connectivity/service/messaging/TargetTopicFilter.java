/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PlaceholderFactory;

/**
 * Classifies and evaluates connection target topic filters.
 * <p>
 * A target topic may carry up to two {@code filter} query parameters which are combined with AND semantics: at
 * most one RQL expression (any value not starting with {@code fn:}, unchanged existing behavior) and at most one
 * placeholder pipeline expression starting with {@code fn:}
 * (e.g. {@code fn:filter(header:ditto-originator,'ne','x')}). Several {@code fn:} stages may be chained with
 * {@code |} inside the pipeline parameter — a stage only runs if the previous one resolved, so chaining is AND as
 * well. Both at-most-one arity rules are enforced at connection creation/update time by
 * {@code ConnectionValidator}; the pipeline expression itself is validated via
 * {@link #validatePipelineFilter(String, DittoHeaders)}.
 * <p>
 * Pipeline filters should only reference headers that are stable for the signal's lifetime (such as
 * {@code ditto-originator} or {@code ditto-origin}): for topics with {@code extraFields}, the pipeline is
 * re-evaluated after enrichment, and internal bookkeeping headers such as {@code requested-acks} are mutated
 * between the pre-enrichment gate and that re-evaluation, so filtering on them is not reliable.
 */
public final class TargetTopicFilter {

    /**
     * Prefix that marks the start of a placeholder pipeline expression.
     */
    private static final String FN_PREFIX = "fn:";

    /**
     * Mandatory seed prepended to every pipeline expression before evaluation.
     * <p>
     * A pipeline that starts directly with a function invocation (e.g. {@code fn:filter(...)}) is seeded internally
     * as {@link org.eclipse.ditto.placeholders.PipelineElement#unresolved()} and {@code PipelineFunctionFilter#apply}
     * only ever acts {@code onResolved}. Without a resolved carrier value entering the first user-supplied function
     * stage, a bare {@code fn:filter(...)} pipeline would therefore ALWAYS stay unresolved, regardless of whether the
     * filter itself matches. Prepending {@code fn:default('true')|} guarantees a resolved boolean carrier value is
     * fed into the first user stage, so the eventual resolved/unresolved outcome reflects the filter result rather
     * than the seeding mechanics. The seed value is consumed purely as this boolean carrier and can never leak into
     * the published signal.
     */
    private static final String PIPELINE_SEED = "fn:default('true')|";

    /**
     * Expression resolver used for validating pipeline expressions at connection-creation/update time.
     * All placeholders resolve to a dummy value; {@code ImmutableExpressionResolver} is {@code @Immutable} and
     * thread-safe, so a single static instance can be shared across all validation calls.
     */
    private static final ExpressionResolver VALIDATION_RESOLVER =
            PlaceholderFactory.newExpressionResolverForValidation(Resolvers.getPlaceholders());

    private TargetTopicFilter() {
        throw new AssertionError();
    }

    /**
     * Classifies a single target topic {@code filter} parameter value.
     *
     * @param filter the raw filter parameter value.
     * @return {@code true} if the trimmed value starts with {@code fn:} and is therefore a placeholder pipeline
     * expression, {@code false} if it is an RQL expression.
     */
    public static boolean isPipelineFilter(final String filter) {
        return filter.trim().startsWith(FN_PREFIX);
    }

    /**
     * Partitions a topic's {@code filter} parameter values into RQL and pipeline expressions, preserving order.
     * Each value is trimmed and classified per {@link #isPipelineFilter(String)}.
     * <p>
     * This method never throws and applies no structural rules: after successful connection validation each
     * partition holds at most one entry, but pre-validation input may violate that - both at-most-one rules are
     * enforced by {@code ConnectionValidator}, which needs the command headers for a proper error, while runtime
     * callers defensively AND-evaluate whatever they get. A trimmed-empty non-{@code fn:} value stays a PRESENT
     * (empty) RQL entry, so that validation keeps rejecting empty filters with an
     * {@code InvalidRqlExpressionException}, exactly as an empty filter was rejected before target topic pipeline
     * filters existed.
     *
     * @param filters the raw filter parameter values of one topic.
     * @return the partitioned filter expressions.
     */
    public static PartitionedFilters partition(final List<String> filters) {
        final List<String> rqlExpressions = new ArrayList<>(1);
        final List<String> pipelineExpressions = new ArrayList<>(filters.size());
        for (final String filter : filters) {
            final String trimmed = filter.trim();
            if (trimmed.startsWith(FN_PREFIX)) {
                pipelineExpressions.add(trimmed);
            } else {
                rqlExpressions.add(trimmed);
            }
        }
        return new PartitionedFilters(List.copyOf(rqlExpressions), List.copyOf(pipelineExpressions));
    }

    /**
     * Evaluates a pipeline expression against a signal, resolving placeholders via
     * {@link Resolvers#forSignal(Signal, ConnectionId)}.
     *
     * @param pipelineExpression the pipeline expression (without the mandatory seed).
     * @param signal the signal the filter is evaluated against.
     * @param connectionId the ID of the connection evaluating the filter.
     * @return {@code true} if the pipeline resolves (i.e. the filter matches and the target topic should be
     * published), {@code false} if it stays unresolved (i.e. the topic should be suppressed).
     * @throws DittoRuntimeException if the pipeline expression is malformed or cannot be evaluated. Callers at
     * runtime sites are responsible for catching this per the runtime failure policy and treating it as a
     * non-match.
     */
    public static boolean matchesPipelineFilter(final String pipelineExpression, final Signal<?> signal,
            final ConnectionId connectionId) {
        return matchesPipelineFilter(pipelineExpression, Resolvers.forSignal(signal, connectionId));
    }

    /**
     * Evaluates a pipeline expression using the given expression resolver.
     *
     * @param pipelineExpression the pipeline expression (without the mandatory seed).
     * @param resolver the expression resolver to resolve placeholders in the pipeline expression with.
     * @return {@code true} if the pipeline resolves (i.e. the filter matches and the target topic should be
     * published), {@code false} if it stays unresolved (i.e. the topic should be suppressed).
     * @throws DittoRuntimeException if the pipeline expression is malformed or cannot be evaluated. Callers at
     * runtime sites are responsible for catching this per the runtime failure policy and treating it as a
     * non-match.
     */
    public static boolean matchesPipelineFilter(final String pipelineExpression, final ExpressionResolver resolver) {
        return resolver.resolveAsPipelineElement(PIPELINE_SEED + pipelineExpression).findFirst().isPresent();
    }

    /**
     * Validates a pipeline expression at connection-creation/update time, i.e. strictly: any placeholder/pipeline
     * function error is rejected. Several {@code fn:} stages may be chained with {@code |} inside the one pipeline
     * filter parameter; the resolver's pipeline grammar enforces the structure (quote-aware stage splitting, every
     * stage a function invocation, at most 10 {@code fn:} stages).
     *
     * @param pipelineExpression the pipeline expression (without the mandatory seed) to validate.
     * @param dittoHeaders the headers of the command which triggered the validation, stamped onto the thrown
     * exception for correlation.
     * @throws ConnectionConfigurationInvalidException if the pipeline expression is invalid, e.g. because it
     * references an unknown placeholder function, has an invalid function signature, exceeds the maximum number
     * of pipeline stages, or references an unresolvable placeholder.
     */
    public static void validatePipelineFilter(final String pipelineExpression, final DittoHeaders dittoHeaders) {
        try {
            VALIDATION_RESOLVER.resolveAsPipelineElement(PIPELINE_SEED + pipelineExpression);
        } catch (final DittoRuntimeException e) {
            throw ConnectionConfigurationInvalidException
                    .newBuilder("The target topic pipeline filter expression '" + pipelineExpression +
                            "' is invalid: " + e.getMessage())
                    .description(e.getDescription()
                            .orElse("Check the spelling and syntax of the pipeline expression.")
                    )
                    .cause(e)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    /**
     * Immutable result of {@link TargetTopicFilter#partition(List)}: a topic's filter parameter values, partitioned
     * into RQL and pipeline expressions with their relative order preserved.
     * <p>
     * An empty string is a PRESENT (if empty) RQL entry, not an absent one - see
     * {@link TargetTopicFilter#partition(List)} for why this matters for validation.
     */
    public static final class PartitionedFilters {

        private final List<String> rqlExpressions;
        private final List<String> pipelineExpressions;

        private PartitionedFilters(final List<String> rqlExpressions, final List<String> pipelineExpressions) {
            this.rqlExpressions = rqlExpressions;
            this.pipelineExpressions = pipelineExpressions;
        }

        /**
         * @return the RQL expressions among the topic's filters - at most one entry after successful connection
         * validation, but possibly more for not (yet) validated input.
         */
        public List<String> getRqlExpressions() {
            return rqlExpressions;
        }

        /**
         * @return the pipeline expressions (each without the mandatory seed) among the topic's filters - at most
         * one entry after successful connection validation, but possibly more for not (yet) validated input.
         */
        public List<String> getPipelineExpressions() {
            return pipelineExpressions;
        }

        /**
         * @return whether at least one of the topic's filters is an RQL expression.
         */
        public boolean hasRqlExpression() {
            return !rqlExpressions.isEmpty();
        }

    }

}
