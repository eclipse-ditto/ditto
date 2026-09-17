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
 * Evaluates and validates the placeholder pipeline expressions of a connection target topic's {@code fn-filter}
 * query parameters.
 * <p>
 * A target topic may carry an RQL {@code filter} parameter (unchanged existing behavior, evaluated by the callers
 * against thing data) and any number of {@code fn-filter} parameters, each holding a placeholder pipeline expression
 * which is evaluated per signal against the signal's headers, topic, entity and time. All of them are combined with
 * AND semantics. An expression is published exactly when its pipeline resolves to a value and has a fixed shape:
 * <pre>
 * &lt;placeholder&gt;[|fn:&lt;value stage&gt;...]|fn:filter('&lt;rqlFunction&gt;',&lt;comparedValue&gt;)
 * </pre>
 * e.g. {@code header:ditto-originator|fn:filter('ne','some:subject')}. It starts with the placeholder whose value
 * is filtered and ends with its only {@code fn:filter} stage, in the 2-parameter form which filters the pipeline's
 * own value. A placeholder that does not resolve for a signal (e.g. an absent header) therefore always suppresses
 * the topic; publishing on an absent value is opted into explicitly with an {@code fn:default(...)} stage before
 * the filter. Everything else is rejected at validation time: an expression starting with a function -
 * {@code fn:filter(header:x,'ne','v')} filters an absent header as the empty value and would publish every signal
 * lacking the header -, a filter value passed to {@code fn:filter} as parameter (same trap), a second
 * {@code fn:filter} stage (further conditions go into further {@code fn-filter} parameters), a pipeline ending with
 * anything but {@code fn:filter} (a bare placeholder, a value-producing stage such as {@code fn:upper()} or an
 * {@code fn:default}, which would discard the filter's decision), an {@code fn:delete} anywhere (it would always
 * suppress the topic) and an {@code fn:filter} stage which cannot work (an unknown {@code rqlFunction} name, the
 * compared value {@code 'exists'}, or {@code 'exists','false'}).
 * An {@code fn:} expression placed in the RQL {@code filter} parameter is rejected by {@code ConnectionValidator}
 * using {@link #isFunctionExpression(String)}; an {@code fn-filter} expression itself is validated via
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
     * A leading pipeline stage consisting of a placeholder prefix and a colon only, i.e. a placeholder without a
     * name (e.g. {@code header:}, {@code header :}, {@code thing-json:}). The headers and thing-json placeholders
     * accept any name at grammar level and the validation resolver never resolves placeholder values, so such a
     * stage passes validation but throws an {@link IllegalArgumentException} for every signal at runtime (or, with
     * whitespace before the colon, silently never resolves).
     */
    private static final Pattern PLACEHOLDER_WITHOUT_NAME = Pattern.compile("^[\\w-]+\\s*:\\s*$");

    /**
     * The start of a pipeline led by a placeholder: {@code <prefix>:<name>|fn:}.
     */
    private static final Pattern PLACEHOLDER_FIRST_PIPELINE = Pattern.compile("^\\s*[\\w-]+\\s*:[^|(]*\\|\\s*fn:");

    private static final String FN_FILTER_HINT = "An 'fn-filter' must be a placeholder pipeline expression which " +
            "starts with the placeholder to filter and ends with its only fn:filter stage, e.g. " +
            "header:ditto-originator|fn:filter('ne','some:subject'); further conditions are combined (AND) by " +
            "repeating the 'fn-filter' parameter; RQL expressions belong into the 'filter' parameter.";

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
     * An {@code fn:delete()} stage, which is absorbing: no later stage can resolve a deleted pipeline again, so the
     * topic would never publish wherever the stage sits.
     */
    private static final Pattern DELETE_STAGE = Pattern.compile("^fn:delete\\(");

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
     * Used to reject {@code fn-filter} expressions which do not start with a placeholder.
     *
     * @param expression the raw parameter value.
     * @return {@code true} if the trimmed value starts with {@code fn:}.
     */
    public static boolean isFunctionExpression(final String expression) {
        return expression.trim().startsWith(FN_PREFIX);
    }

    /**
     * Checks whether the given expression is a placeholder pipeline rather than an RQL expression: it starts with a
     * function invocation ({@code fn:}) or with a placeholder followed by one ({@code header:x|fn:...}). An RQL
     * expression starts with {@code <name>(} and therefore never matches, not even with an unquoted {@code |} in a
     * property path. Used to reject pipelines placed in the RQL {@code filter} parameter with a hint to
     * {@code fn-filter}.
     *
     * @param expression the raw parameter value.
     * @return {@code true} if the value is a placeholder pipeline expression.
     */
    public static boolean isPipelineExpression(final String expression) {
        return isFunctionExpression(expression) || PLACEHOLDER_FIRST_PIPELINE.matcher(expression).find();
    }

    /**
     * Evaluates an {@code fn-filter} expression against a signal, resolving placeholders via
     * {@link Resolvers#forSignal(Signal, ConnectionId)}.
     *
     * @param fnFilter the raw {@code fn-filter} parameter value.
     * @param signal the signal the filter is evaluated against.
     * @param connectionId the ID of the connection evaluating the filter.
     * @return {@code true} if the pipeline resolves to a value (the target topic should be published),
     * {@code false} if it stays unresolved or is deleted (the topic should be suppressed). An expression starting
     * with a function - rejected by {@link #validateFnFilter(String, DittoHeaders)} - never resolves.
     * @throws RuntimeException if the expression is malformed or cannot be evaluated - a
     * {@link DittoRuntimeException} from the pipeline grammar, or a plain runtime exception thrown while a
     * placeholder resolves its value (e.g. an {@link IllegalArgumentException} for a placeholder without a name,
     * which the validation resolver cannot detect). Runtime callers are responsible for catching
     * {@code RuntimeException} per the runtime failure policy and treating it as a non-match.
     */
    public static boolean matchesFnFilter(final String fnFilter, final Signal<?> signal,
            final ConnectionId connectionId) {
        return Resolvers.forSignal(signal, connectionId)
                .resolveAsPipelineElement(fnFilter.trim())
                .findFirst()
                .isPresent();
    }

    /**
     * Validates an {@code fn-filter} expression at connection-creation/update time, i.e. strictly: any
     * placeholder/pipeline function error is rejected. The resolver's pipeline grammar enforces the structure
     * (quote-aware stage splitting, a leading placeholder, every further stage a function invocation, at most 10
     * {@code fn:} stages). Only thrown errors are checked - the resolved result is meaningless in validation mode,
     * where every placeholder resolves to a dummy value. Additionally, a leading placeholder without a name (e.g.
     * {@code header:}) is rejected, because it passes the grammar but cannot be evaluated.
     * <p>
     * Beyond the grammar, the expression must have the shape described in the {@link TargetTopicFilter class
     * documentation}: it starts with a placeholder, its only {@code fn:filter} stage is the last stage and has
     * the form {@code fn:filter('<rqlFunction>',<comparedValue>)} with a known literal {@code rqlFunction} -
     * which guarantees that a placeholder which does not resolve suppresses the topic instead of being filtered
     * as the empty value - and it contains no {@code fn:delete()}.
     *
     * @param fnFilter the raw {@code fn-filter} parameter value to validate.
     * @param dittoHeaders the headers of the command which triggered the validation, stamped onto the thrown
     * exception for correlation.
     * @throws ConnectionConfigurationInvalidException if the expression is invalid, e.g. because it is empty, is an
     * RQL expression, starts with a function, references an unknown placeholder or function, starts with a
     * placeholder without a name, has an invalid function signature, exceeds the maximum number of pipeline stages,
     * does not end with its only {@code fn:filter} stage, or that stage is not of the supported form.
     */
    public static void validateFnFilter(final String fnFilter, final DittoHeaders dittoHeaders) {
        final String pipelineExpression = fnFilter.trim();
        if (isFunctionExpression(pipelineExpression)) {
            throw invalidFnFilter(fnFilter, "The expression must start with a placeholder, the value to filter, " +
                    "not with a function: a placeholder inside fn:filter(...) is filtered as the empty value when " +
                    "it does not resolve (e.g. an absent header), so that 'ne' would publish every such signal." +
                    suggestPlaceholderFirstRewrite(pipelineExpression), null, dittoHeaders);
        }
        try {
            VALIDATION_RESOLVER.resolveAsPipelineElement(pipelineExpression);
        } catch (final DittoRuntimeException e) {
            throw invalidFnFilter(fnFilter, e.getMessage(), e, dittoHeaders);
        }
        // the grammar has been validated above, so the split into stages/parameters is guaranteed to agree with it
        final List<String> stages = splitQuoteAware(PIPELINE_STAGE, pipelineExpression);
        final String leadingStage = stages.get(0);
        if (PLACEHOLDER_WITHOUT_NAME.matcher(leadingStage).matches()) {
            throw invalidFnFilter(fnFilter, "The leading placeholder '" + leadingStage + "' has no name.", null,
                    dittoHeaders);
        }
        for (final String stage : stages) {
            if (DELETE_STAGE.matcher(stage).find()) {
                throw invalidFnFilter(fnFilter, "The stage '" + stage + "' would make the topic never publish - " +
                        "no later stage can resolve a deleted pipeline again.", null, dittoHeaders);
            }
        }
        final String lastStage = stages.get(stages.size() - 1);
        final Matcher filterStage = FILTER_STAGE.matcher(lastStage);
        if (!filterStage.matches()) {
            throw invalidFnFilter(fnFilter, "The last stage '" + lastStage + "' is not a filter stage - the last " +
                    "stage must be fn:filter(...), the stage that yields the publish decision. A bare placeholder " +
                    "does not filter anything: to publish exactly when it resolves, say so explicitly with " +
                    "<placeholder>|fn:filter('exists','true'), e.g. header:ditto-originator|fn:filter('exists'," +
                    "'true'); a trailing value-producing stage such as fn:upper() cannot change the preceding " +
                    "decision and a trailing fn:default(...) would discard it.", null, dittoHeaders);
        }
        for (final String stage : stages.subList(0, stages.size() - 1)) {
            if (FILTER_STAGE.matcher(stage).matches()) {
                throw invalidFnFilter(fnFilter, "The stage '" + stage + "' is not the last stage, but only one " +
                        "fn:filter stage is supported per expression - to combine several conditions (AND) " +
                        "repeat the 'fn-filter' parameter, e.g. ?fn-filter=header:ditto-originator|fn:filter(" +
                        "'ne','some:subject')&fn-filter=header:ditto-origin|fn:filter('ne','some-connection-id').",
                        null, dittoHeaders);
            }
        }
        validateFilterStage(fnFilter, lastStage, splitQuoteAware(FUNCTION_PARAMETER, filterStage.group(1)),
                dittoHeaders);
    }

    /**
     * Accepts only {@code fn:filter('<rqlFunction>',<comparedValue>)} - the 2-parameter form which filters the
     * pipeline's own value - with a known literal {@code rqlFunction}. A filter value passed as parameter (the
     * 3-parameter form and {@code fn:filter(<filterValue>,'exists')}) is either a placeholder, filtered as the empty
     * value when it does not resolve, or a constant which never looks at the signal; a placeholder-valued
     * {@code rqlFunction} cannot be told apart from such a filter value.
     */
    private static void validateFilterStage(final String fnFilter, final String stage, final List<String> params,
            final DittoHeaders dittoHeaders) {
        if (params.size() != 2 || !isQuotedConstant(params.get(0))) {
            throw invalidFnFilter(fnFilter, "The stage '" + stage + "' is not of the form " +
                    "fn:filter('<rqlFunction>',<comparedValue>), which filters the value of the preceding " +
                    "pipeline: the value to filter is never passed to fn:filter as a parameter (a placeholder " +
                    "passed that way is filtered as the empty value when it does not resolve) and the " +
                    "rqlFunction must be a quoted constant - start a separate 'fn-filter' parameter with the " +
                    "placeholder instead.", null, dittoHeaders);
        }
        final String rqlFunction = unquote(params.get(0));
        final String comparedValueParam = params.get(1);
        final boolean comparedValueIsConstant = isQuotedConstant(comparedValueParam);
        if (!RQL_FUNCTION_NAMES.contains(rqlFunction)) {
            throw invalidFnFilter(fnFilter, "The stage '" + stage + "' uses the unknown rqlFunction " +
                    params.get(0) + " which never matches - supported (case-sensitive) rqlFunction names are " +
                    RQL_FUNCTION_NAMES.stream().sorted().map(name -> "'" + name + "'")
                            .collect(Collectors.joining(", ")) + ".",
                    null, dittoHeaders);
        }
        if (comparedValueIsConstant && EXISTS_FUNCTION_NAME.equals(unquote(comparedValueParam))) {
            throw invalidFnFilter(fnFilter, "The stage '" + stage + "' uses the compared value 'exists', which " +
                    "fn:filter takes for its fn:filter(<filterValue>,'exists') form: the stage would match every " +
                    "resolved value. To publish exactly when the placeholder resolves use fn:filter('exists'," +
                    "'true').", null, dittoHeaders);
        }
        if (EXISTS_FUNCTION_NAME.equals(rqlFunction) && comparedValueIsConstant &&
                !Boolean.parseBoolean(unquote(comparedValueParam))) {
            throw invalidFnFilter(fnFilter, "The stage '" + stage + "' would make the topic never publish: the " +
                    "filtered value is the resolved value of the preceding pipeline, which always exists. To " +
                    "publish when a placeholder does not resolve, supply a value for that case and filter on it: " +
                    "<placeholder>|fn:default('<none>')|fn:filter('eq','<none>').", null, dittoHeaders);
        }
    }

    /**
     * For the simple function-first expressions {@code fn:filter(<placeholder>,'<rqlFunction>',<comparedValue>)} and
     * {@code fn:filter(<placeholder>,'exists')} the equivalent placeholder-first expression, as a sentence to
     * append to the error message - or the empty string if the expression is not that simple.
     */
    private static String suggestPlaceholderFirstRewrite(final String pipelineExpression) {
        final Matcher filterStage = FILTER_STAGE.matcher(pipelineExpression);
        if (!filterStage.matches() || splitQuoteAware(PIPELINE_STAGE, pipelineExpression).size() != 1) {
            return "";
        }
        final List<String> params = splitQuoteAware(FUNCTION_PARAMETER, filterStage.group(1));
        if (params.size() < 2 || isQuotedConstant(params.get(0)) || !isQuotedConstant(params.get(1))) {
            return "";
        }
        final String comparedValue = params.size() == 3 ? params.get(2) : "'true'";
        return " Write " + params.get(0) + "|fn:filter(" + params.get(1) + "," + comparedValue + ") instead.";
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

}
