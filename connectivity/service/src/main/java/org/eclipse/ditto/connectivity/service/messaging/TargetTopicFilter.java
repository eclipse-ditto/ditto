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

import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PlaceholderFactory;

/**
 * Evaluates and validates the placeholder pipeline expression of a connection target topic's {@code fn-filter}
 * query parameter.
 * <p>
 * A target topic may carry two independent filter parameters which are combined with AND semantics: {@code filter}
 * holds an RQL expression (unchanged existing behavior, evaluated by the callers against thing data) and
 * {@code fn-filter} holds a placeholder pipeline expression which is evaluated per signal against the signal's
 * headers, topic, entity and time. The pipeline may start with a placeholder
 * ({@code header:ditto-originator|fn:filter('ne','x')}) or directly with a function
 * ({@code fn:filter(header:ditto-originator,'ne','x')}); further {@code fn:} stages may be chained with {@code |} -
 * a stage only runs if the previous one resolved, so chaining is AND as well. The topic is published exactly when
 * the pipeline resolves to a value, which is why the last stage should be {@code fn:filter}: a trailing
 * {@code fn:default} would make the topic always publish, a trailing {@code fn:delete} would always suppress it.
 * An {@code fn:} expression placed in the RQL {@code filter} parameter is rejected by {@code ConnectionValidator}
 * using {@link #isFunctionExpression(String)}; the {@code fn-filter} expression itself is validated via
 * {@link #validateFnFilter(String, DittoHeaders)}.
 * <p>
 * Pipeline filters should only reference headers that are stable for the signal's lifetime (such as
 * {@code ditto-originator} or {@code ditto-origin}): for topics with {@code extraFields}, the pipeline is
 * re-evaluated after enrichment, and internal bookkeeping headers such as {@code requested-acks} are mutated
 * between the pre-enrichment gate and that re-evaluation, so filtering on them is not reliable.
 */
public final class TargetTopicFilter {

    /**
     * Prefix of a placeholder function invocation.
     */
    private static final String FN_PREFIX = "fn:";

    /**
     * Seed prepended to a pipeline expression that starts directly with a function invocation.
     * <p>
     * Such a pipeline is seeded by the resolver as {@link org.eclipse.ditto.placeholders.PipelineElement#unresolved()}
     * and {@code PipelineFunctionFilter#apply} only ever acts {@code onResolved}: without a resolved carrier value
     * a bare {@code fn:filter(...)} pipeline would always stay unresolved, regardless of whether the filter matches.
     * {@code fn:default('true')} turns the unresolved seed into a resolved carrier that can never leak into the
     * published signal. A pipeline that starts with a placeholder gets its carrier from that placeholder and must
     * NOT be seeded - the seed would push the placeholder into the second stage, where the pipeline grammar only
     * accepts {@code fn:} function invocations. The seed occupies one of the resolver's 11 pipeline slots, leaving
     * 10 user {@code fn:} stages in both forms.
     */
    private static final String FUNCTION_FIRST_SEED = "fn:default('true')|";

    /**
     * A leading pipeline stage consisting of a placeholder prefix and a colon only, i.e. a placeholder without a
     * name (e.g. {@code header:}, {@code header :}, {@code thing-json:}). The headers and thing-json placeholders
     * accept any name at grammar level and the validation resolver never resolves placeholder values, so such a
     * stage passes validation but throws an {@link IllegalArgumentException} for every signal at runtime (or, with
     * whitespace before the colon, silently never resolves).
     */
    private static final Pattern PLACEHOLDER_WITHOUT_NAME = Pattern.compile("^[\\w-]+\\s*:\\s*$");

    private static final String FN_FILTER_HINT = "An 'fn-filter' must be a placeholder pipeline expression whose " +
            "last stage is fn:filter(...), e.g. fn:filter(header:ditto-originator,'ne','some:subject') or " +
            "header:ditto-originator|fn:filter('ne','some:subject'); RQL expressions belong into the 'filter' " +
            "parameter.";

    /**
     * Expression resolver used for validating pipeline expressions at connection-creation/update time.
     * All placeholders resolve to a dummy value (the empty string) and placeholder values are never resolved;
     * {@code ImmutableExpressionResolver} is {@code @Immutable} and thread-safe, so a single static instance can be
     * shared across all validation calls.
     */
    private static final ExpressionResolver VALIDATION_RESOLVER =
            PlaceholderFactory.newExpressionResolverForValidation(Resolvers.getPlaceholders());

    private TargetTopicFilter() {
        throw new AssertionError();
    }

    /**
     * Checks whether the given (trimmed) expression starts with a placeholder function invocation ({@code fn:}).
     * Used to reject function expressions placed in the RQL {@code filter} parameter and to decide whether an
     * {@code fn-filter} pipeline needs the internal seed.
     *
     * @param expression the raw parameter value.
     * @return {@code true} if the trimmed value starts with {@code fn:}.
     */
    public static boolean isFunctionExpression(final String expression) {
        return expression.trim().startsWith(FN_PREFIX);
    }

    /**
     * Evaluates an {@code fn-filter} expression against a signal, resolving placeholders via
     * {@link Resolvers#forSignal(Signal, ConnectionId)}.
     *
     * @param fnFilter the raw {@code fn-filter} parameter value.
     * @param signal the signal the filter is evaluated against.
     * @param connectionId the ID of the connection evaluating the filter.
     * @return {@code true} if the pipeline resolves to a value (the target topic should be published),
     * {@code false} if it stays unresolved or is deleted (the topic should be suppressed).
     * @throws RuntimeException if the expression is malformed or cannot be evaluated - a
     * {@link DittoRuntimeException} from the pipeline grammar, or a plain runtime exception thrown while a
     * placeholder resolves its value (e.g. an {@link IllegalArgumentException} for a placeholder without a name,
     * which the validation resolver cannot detect). Runtime callers are responsible for catching
     * {@code RuntimeException} per the runtime failure policy and treating it as a non-match.
     */
    public static boolean matchesFnFilter(final String fnFilter, final Signal<?> signal,
            final ConnectionId connectionId) {
        return Resolvers.forSignal(signal, connectionId)
                .resolveAsPipelineElement(toPipelineExpression(fnFilter))
                .findFirst()
                .isPresent();
    }

    /**
     * Validates an {@code fn-filter} expression at connection-creation/update time, i.e. strictly: any
     * placeholder/pipeline function error is rejected. The resolver's pipeline grammar enforces the structure
     * (quote-aware stage splitting, a leading placeholder or function, every further stage a function invocation,
     * at most 10 {@code fn:} stages). Only thrown errors are checked - the resolved result is meaningless in
     * validation mode, where every placeholder resolves to a dummy value. Additionally, a leading placeholder
     * without a name (e.g. {@code header:}) is rejected, because it passes the grammar but cannot be evaluated.
     *
     * @param fnFilter the raw {@code fn-filter} parameter value to validate.
     * @param dittoHeaders the headers of the command which triggered the validation, stamped onto the thrown
     * exception for correlation.
     * @throws ConnectionConfigurationInvalidException if the expression is invalid, e.g. because it is empty, is an
     * RQL expression, references an unknown placeholder or function, starts with a placeholder without a name, has
     * an invalid function signature or exceeds the maximum number of pipeline stages.
     */
    public static void validateFnFilter(final String fnFilter, final DittoHeaders dittoHeaders) {
        final String pipelineExpression = toPipelineExpression(fnFilter);
        try {
            VALIDATION_RESOLVER.resolveAsPipelineElement(pipelineExpression);
        } catch (final DittoRuntimeException e) {
            throw invalidFnFilter(fnFilter, e.getMessage(), e, dittoHeaders);
        }
        if (!isFunctionExpression(pipelineExpression)) {
            // a leading placeholder stage never contains quotes, so the first pipe ends it
            final int pipeIndex = pipelineExpression.indexOf('|');
            final String leadingStage =
                    (pipeIndex < 0 ? pipelineExpression : pipelineExpression.substring(0, pipeIndex)).trim();
            if (PLACEHOLDER_WITHOUT_NAME.matcher(leadingStage).matches()) {
                final DittoRuntimeException noCause = null;
                throw invalidFnFilter(fnFilter, "The leading placeholder '" + leadingStage + "' has no name.",
                        noCause, dittoHeaders);
            }
        }
    }

    private static ConnectionConfigurationInvalidException invalidFnFilter(final String fnFilter,
            final String reason, @Nullable final DittoRuntimeException cause, final DittoHeaders dittoHeaders) {
        final String causeDescription = null != cause
                ? cause.getDescription().map(description -> description + " ").orElse("")
                : "";
        return ConnectionConfigurationInvalidException
                .newBuilder("The target topic 'fn-filter' expression '" + fnFilter + "' is invalid: " + reason)
                .description(causeDescription + FN_FILTER_HINT)
                .cause(cause)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private static String toPipelineExpression(final String fnFilter) {
        final String trimmed = fnFilter.trim();
        return isFunctionExpression(trimmed) ? FUNCTION_FIRST_SEED + trimmed : trimmed;
    }

}
