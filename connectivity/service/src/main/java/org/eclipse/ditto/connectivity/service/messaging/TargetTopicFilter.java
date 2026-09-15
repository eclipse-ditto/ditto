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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.filter.FilterFunctions;

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
 * {@code fn:default} would make the topic always publish, a trailing {@code fn:delete} would always suppress it -
 * both are rejected at validation time, as are {@code fn:filter} stages that can never match (an unknown literal
 * {@code rqlFunction} name, or {@code eq}/{@code ne}/{@code like} without a compared value).
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
     * The names of the RQL functions {@code fn:filter} understands, e.g. {@code eq}, {@code ne}, {@code like},
     * {@code exists}. Looked up case-sensitively by the pipeline function, so {@code 'NE'} or {@code 'neq'} is an
     * unknown function that never matches - such a literal is rejected at validation time.
     */
    private static final Set<String> RQL_FUNCTION_NAMES = Arrays.stream(FilterFunctions.values())
            .map(FilterFunctions::getName)
            .collect(Collectors.toUnmodifiableSet());

    private static final String EXISTS_FUNCTION_NAME = FilterFunctions.EXISTS.getName();

    /**
     * A quoted string constant as the pipeline grammar defines it (mirrors the package-private
     * {@code PipelineFunction#SINGLE_QUOTED_STRING_CONTENT}/{@code DOUBLE_QUOTED_STRING_CONTENT}: a backslash
     * escapes the quote), so the stage/parameter split below agrees with the grammar - the resolver has already
     * validated the expression against it when this split runs.
     */
    private static final String QUOTED_CONSTANT = "'(?:\\\\'|[^'])*+'|\"(?:\\\\\"|[^\"])*+\"";

    /**
     * One pipeline stage: anything but an unquoted {@code |}, i.e. the resolver's stage pattern.
     */
    private static final Pattern PIPELINE_STAGE = Pattern.compile("(?:[^|'\"]++|" + QUOTED_CONSTANT + ")++");

    /**
     * One function parameter: anything but an unquoted {@code ,}.
     */
    private static final Pattern FUNCTION_PARAMETER = Pattern.compile("(?:[^,'\"]++|" + QUOTED_CONSTANT + ")++");

    /**
     * An {@code fn:filter(...)} stage with its parameter list (group 1, without the parentheses).
     */
    private static final Pattern FILTER_STAGE = Pattern.compile("^fn:filter\\((.*)\\)$", Pattern.DOTALL);

    /**
     * A trailing {@code fn:default(...)} or {@code fn:delete()} stage (group 1 = the function name), which would
     * turn the publish decision into a constant.
     */
    private static final Pattern CONSTANT_TERMINAL_STAGE = Pattern.compile("^fn:(default|delete)\\(");

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
     * <p>
     * Beyond the grammar, expressions whose publish decision is a constant regardless of the signal are rejected,
     * because they silently publish nothing (or everything): an {@code fn:filter} stage with an unknown literal
     * {@code rqlFunction} name (e.g. {@code 'NE'}) or with {@code eq}/{@code ne}/{@code like} but no compared value
     * never matches, a trailing {@code fn:default(...)} always publishes and a trailing {@code fn:delete()} never
     * does. These checks only look at literals - a placeholder-valued {@code rqlFunction} is skipped.
     *
     * @param fnFilter the raw {@code fn-filter} parameter value to validate.
     * @param dittoHeaders the headers of the command which triggered the validation, stamped onto the thrown
     * exception for correlation.
     * @throws ConnectionConfigurationInvalidException if the expression is invalid, e.g. because it is empty, is an
     * RQL expression, references an unknown placeholder or function, starts with a placeholder without a name, has
     * an invalid function signature, exceeds the maximum number of pipeline stages, or can only ever publish
     * nothing or everything.
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
                throw invalidFnFilter(fnFilter, "The leading placeholder '" + leadingStage + "' has no name.",
                        null, dittoHeaders);
            }
        }
        // the grammar has been validated above, so the split into stages/parameters is guaranteed to agree with it
        final List<String> stages = splitQuoteAware(PIPELINE_STAGE, fnFilter.trim());
        final Matcher terminalStage = CONSTANT_TERMINAL_STAGE.matcher(stages.get(stages.size() - 1));
        if (terminalStage.find()) {
            final String function = "fn:" + terminalStage.group(1);
            final String effect = "default".equals(terminalStage.group(1)) ? "always" : "never";
            throw invalidFnFilter(fnFilter, "The last stage '" + function + "' would make the topic " + effect +
                    " publish - the last stage must be fn:filter(...).", null, dittoHeaders);
        }
        for (final String stage : stages) {
            final Matcher filterStage = FILTER_STAGE.matcher(stage);
            if (filterStage.matches()) {
                validateFilterStage(fnFilter, stage, splitQuoteAware(FUNCTION_PARAMETER, filterStage.group(1)),
                        dittoHeaders);
            }
        }
    }

    /**
     * Rejects an {@code fn:filter} stage that can never match: {@code fn:filter(filterValue, rqlFunction,
     * comparedValue)} with an unknown literal {@code rqlFunction}, or the 2-parameter form
     * {@code fn:filter(rqlFunction, comparedValue)} / {@code fn:filter(filterValue, 'exists')} that names
     * {@code eq}/{@code ne}/{@code like} with a placeholder as first parameter, i.e. without a compared value.
     * Unquoted parameters are placeholders and cannot be checked at validation time.
     */
    private static void validateFilterStage(final String fnFilter, final String stage, final List<String> params,
            final DittoHeaders dittoHeaders) {
        if (params.size() == 3) {
            rejectUnknownRqlFunctionLiteral(fnFilter, stage, params.get(1), dittoHeaders);
        } else if (params.size() == 2) {
            final String first = params.get(0);
            final String second = params.get(1);
            if (isQuotedConstant(second) && EXISTS_FUNCTION_NAME.equals(unquote(second))) {
                return; // fn:filter(<value>, 'exists')
            }
            if (isQuotedConstant(first)) {
                rejectUnknownRqlFunctionLiteral(fnFilter, stage, first, dittoHeaders); // fn:filter('ne', <value>)
            } else if (isQuotedConstant(second)) {
                // fn:filter(<placeholder>, 'eq') - the placeholder would be taken as the rqlFunction name
                throw invalidFnFilter(fnFilter, "The stage '" + stage + "' has no compared value: with a " +
                        "placeholder as first parameter the 2-parameter form only supports 'exists', " +
                        "fn:filter(<placeholder>,'exists'); 'eq', 'ne' and 'like' require a compared value, " +
                        "e.g. fn:filter(<placeholder>,'ne','some:subject').", null, dittoHeaders);
            }
        }
    }

    private static void rejectUnknownRqlFunctionLiteral(final String fnFilter, final String stage,
            final String rqlFunctionParam, final DittoHeaders dittoHeaders) {
        if (isQuotedConstant(rqlFunctionParam) && !RQL_FUNCTION_NAMES.contains(unquote(rqlFunctionParam))) {
            throw invalidFnFilter(fnFilter, "The stage '" + stage + "' uses the unknown rqlFunction " +
                    rqlFunctionParam + " which never matches - supported (case-sensitive) rqlFunction names are " +
                    RQL_FUNCTION_NAMES.stream().sorted().map(name -> "'" + name + "'")
                            .collect(Collectors.joining(", ")) + ".",
                    null, dittoHeaders);
        }
    }

    private static List<String> splitQuoteAware(final Pattern elementPattern, final String input) {
        final List<String> elements = new ArrayList<>();
        final Matcher matcher = elementPattern.matcher(input);
        while (matcher.find()) {
            elements.add(matcher.group().trim());
        }
        return elements;
    }

    private static boolean isQuotedConstant(final String param) {
        return param.startsWith("'") || param.startsWith("\"");
    }

    private static String unquote(final String quotedConstant) {
        return quotedConstant.substring(1, quotedConstant.length() - 1);
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
