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

import java.util.Optional;
import java.util.regex.Matcher;
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
 * Parses and evaluates connection target topic filters.
 * <p>
 * A target topic filter may be a pure RQL expression (unchanged, existing behavior), a pure placeholder pipeline
 * expression starting with {@code fn:} (e.g. {@code fn:filter(header:ditto-originator,'ne','x')}), or a combination
 * of both joined with an unquoted {@code |} (RQL head followed by the pipeline, combined with AND semantics by
 * callers of this class).
 * <p>
 * Two combined-filter caveats, both failing loudly at connection creation/update time: an RQL property path that
 * literally contains {@code |fn:} is misread as the start of a pipeline (the split head is then no valid RQL), and
 * an unescaped bare {@code '} in an RQL property path (legal in RQL) leaves the quote-tracking scan of
 * {@link #parse(String)} "inside a quote", so the combined filter is never split and the whole string is rejected
 * by the RQL parser. Restructure such filters (e.g. rename the property) to avoid the offending character sequence.
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
     * Matches an (optionally whitespace-prefixed) {@code fn:} right after an unquoted anchor {@code |}.
     * Compiled once and reused via {@link Matcher#region(int, int)} to avoid recompilation per {@link #parse(String)}
     * call.
     */
    private static final Pattern PIPELINE_ANCHOR_PATTERN = Pattern.compile("\\s*fn:");

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
     * Parses a raw target topic filter string into its RQL and/or pipeline parts.
     * <p>
     * Grammar (see also the class-level documentation):
     * <ul>
     * <li>The filter string is trimmed first.</li>
     * <li>If the trimmed string starts with {@code fn:}, the whole trimmed string is the pipeline expression and
     * the RQL head is absent.</li>
     * <li>Otherwise, if the trimmed string contains no {@code fn:} substring at all, the whole trimmed string is
     * returned verbatim as the RQL expression (fast path, no scanning) - this is essential for backwards
     * compatibility, because an unquoted {@code |} is legal today in RQL property paths
     * (e.g. {@code eq(attributes/a|b,1)}).</li>
     * <li>Otherwise, the trimmed string is scanned once, tracking single-/double-quote state, for the first
     * unquoted {@code |} whose next non-space characters are {@code fn:} (the "anchor"). If such an anchor is
     * found, the text before it is the RQL head and everything from directly after it to the end of the string
     * is the pipeline expression. If no anchor is found (e.g. all {@code fn:} occurrences are inside quoted RQL
     * string literals), the whole trimmed string is again returned verbatim as the RQL expression.</li>
     * </ul>
     * This method never throws; malformed RQL keeps failing later at RQL-parse time and malformed/rejected pipeline
     * expressions are only rejected in {@link #validatePipelineFilter(String, DittoHeaders)}.
     *
     * @param filter the raw target topic filter string.
     * @return the parsed filter, exposing the optional RQL and/or pipeline parts. The RQL part is only absent for
     * a pure pipeline filter (whole string starts with {@code fn:}) or a leading-pipe combined filter with an empty
     * RQL head (e.g. {@code |fn:...}); in particular an empty or whitespace-only filter string yields an RQL part
     * that is PRESENT (an empty string), not absent - see the class-level documentation for why this matters for
     * validation.
     */
    public static ParsedTopicFilter parse(final String filter) {
        final String trimmed = filter.trim();

        if (trimmed.startsWith(FN_PREFIX)) {
            return new ParsedTopicFilter(null, trimmed);
        }
        if (!trimmed.contains(FN_PREFIX)) {
            return new ParsedTopicFilter(trimmed, null);
        }

        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        final Matcher anchorMatcher = PIPELINE_ANCHOR_PATTERN.matcher(trimmed);
        for (int i = 0; i < trimmed.length(); i++) {
            final char c = trimmed.charAt(i);
            if (c == '\\') {
                // RQL allows '\' escapes both inside quoted literals and in property literals ('\' ~ EscapedChar);
                // unconditionally skip the escaped character so it can never toggle quote state or be mistaken
                // for an anchor '|'. Safe at end-of-string: the extra skip just runs the loop condition false.
                i++;
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (c == '|' && !inSingleQuote && !inDoubleQuote) {
                anchorMatcher.region(i + 1, trimmed.length());
                if (anchorMatcher.lookingAt()) {
                    // the pipeline part can never be empty by construction (the anchor requires "fn:" right after
                    // the "|"); the RQL head, however, is empty for a leading-pipe filter (e.g. "|fn:..."), which
                    // is then a pure pipeline expression - pass null explicitly rather than an empty string so it
                    // is treated as absent, not as an (invalid, empty) RQL expression.
                    final String rqlHead = trimmed.substring(0, i).trim();
                    return new ParsedTopicFilter(rqlHead.isEmpty() ? null : rqlHead, trimmed.substring(i + 1));
                }
            }
        }

        // "fn:" occurs somewhere (e.g. inside a quoted RQL literal) but never as an unquoted pipeline anchor.
        return new ParsedTopicFilter(trimmed, null);
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
     * function error is rejected.
     *
     * @param pipelineExpression the pipeline expression (without the mandatory seed) to validate.
     * @param dittoHeaders the headers of the command which triggered the validation, stamped onto the thrown
     * exception for correlation.
     * @throws ConnectionConfigurationInvalidException if the pipeline expression is invalid, e.g. because it
     * references an unknown placeholder function, has an invalid function signature, references an unresolvable
     * placeholder, or exceeds the maximum number of pipeline stages.
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
     * Immutable result of {@link TargetTopicFilter#parse(String)}: the RQL and/or pipeline parts of a target topic
     * filter, either of which may be absent.
     * <p>
     * {@code null} is the only "absent" marker for either part - in particular an empty string is a PRESENT (if
     * empty) RQL expression, not an absent one. This matters because {@link TargetTopicFilter#parse(String)} on an
     * empty or whitespace-only filter must yield a present-but-empty RQL part, so that callers which route a
     * present RQL part into RQL parsing/validation (e.g. {@code ConnectionValidator}) reject it with an
     * {@code InvalidRqlExpressionException}, exactly as an empty filter was rejected before target topic pipeline
     * filters existed.
     */
    public static final class ParsedTopicFilter {

        @Nullable private final String rqlExpression;
        @Nullable private final String pipelineExpression;

        private ParsedTopicFilter(@Nullable final String rqlExpression, @Nullable final String pipelineExpression) {
            this.rqlExpression = rqlExpression;
            this.pipelineExpression = pipelineExpression;
        }

        /**
         * @return the RQL head of the filter, or an empty Optional if the filter is a pure pipeline expression.
         */
        public Optional<String> getRqlExpression() {
            return Optional.ofNullable(rqlExpression);
        }

        /**
         * @return the pipeline expression of the filter (without the mandatory seed), or an empty Optional if the
         * filter is pure RQL.
         */
        public Optional<String> getPipelineExpression() {
            return Optional.ofNullable(pipelineExpression);
        }

    }

}
