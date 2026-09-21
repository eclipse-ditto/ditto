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
 * A target topic may carry an RQL {@code filter} parameter, which is evaluated by the callers of this class, and any
 * number of {@code fn-filter} parameters, each holding a placeholder pipeline expression which is evaluated per
 * signal with the placeholders of {@link Resolvers#forSignal(Signal, ConnectionId)}; {@code thing-json} only sees
 * the thing data carried by the signal itself, not the enriched {@code extraFields}. All of them are combined with
 * AND semantics: an {@code fn-filter} matches when its pipeline resolves to a value. An expression has a fixed
 * shape:
 * <pre>
 * &lt;placeholder&gt;[|fn:&lt;value stage&gt;...]|fn:filter('&lt;rqlFunction&gt;',&lt;comparedValue&gt;)
 * </pre>
 * e.g. {@code header:ditto-originator|fn:filter('ne','some:subject')}. It starts with the placeholder whose value
 * is filtered and ends with its only {@code fn:filter} stage, in the 2-parameter form which filters the pipeline's
 * own value. A placeholder that does not resolve for a signal (e.g. an absent header) therefore always suppresses
 * the topic; publishing on an absent value requires an {@code fn:default(...)} stage before the filter. A
 * placeholder passed to {@code fn:filter} as parameter would instead be filtered as the empty value when it does
 * not resolve, so that {@code 'ne'} would publish every signal lacking the header. All other shapes are rejected by
 * {@link #validateFnFilter(String, DittoHeaders)}.
 * A placeholder pipeline placed in the RQL {@code filter} parameter is rejected by {@code ConnectionValidator}
 * using {@link #isPipelineExpression(String)}.
 * <p>
 * An {@code fn-filter} should only reference headers that are stable for the signal's lifetime (such as
 * {@code ditto-originator} or {@code ditto-origin}): for topics with {@code extraFields}, the pipeline is
 * re-evaluated after enrichment, and internal bookkeeping headers such as {@code requested-acks} are mutated
 * between the evaluation in {@code SignalFilter} and that re-evaluation, so filtering on them is not reliable.
 */
public final class TargetTopicFilter {

    /**
     * Prefix of a placeholder function invocation.
     */
    private static final String FN_PREFIX = "fn:";

    /**
     * The start of an {@code fn:filter(...)} stage.
     */
    private static final String FILTER_STAGE_START = FN_PREFIX + "filter(";

    /**
     * A leading pipeline stage consisting of a placeholder prefix and a colon only, i.e. a placeholder without a
     * name (e.g. {@code header:}, {@code header :}, {@code thing-json:}). The headers and thing-json placeholders
     * accept any name at grammar level and the validation resolver never resolves placeholder values, so such a
     * stage passes the grammar but throws an {@link IllegalArgumentException} for every signal at runtime (or, with
     * whitespace before the colon, silently never resolves).
     */
    private static final Pattern PLACEHOLDER_WITHOUT_NAME = Pattern.compile("^[\\w-]+\\s*:\\s*$");

    /**
     * The prefix (group 1) and the colon of a leading placeholder together with the whitespace around the colon
     * (group 2). The headers and thing-json placeholders accept any name at grammar level, so such whitespace
     * passes the grammar, but it is not dropped when the value is looked up: {@code header: x} and
     * {@code header :x} never find the header {@code x}.
     */
    private static final Pattern PLACEHOLDER_PREFIX_AND_COLON = Pattern.compile("^([\\w-]+)(\\s*:\\s*)");

    /**
     * The start of a pipeline led by a placeholder: {@code <prefix>:<name>|fn:}.
     */
    private static final Pattern PLACEHOLDER_FIRST_PIPELINE = Pattern.compile("^\\s*[\\w-]+\\s*:[^|(]*\\|\\s*fn:");

    private static final String FN_FILTER_HINT = "An 'fn-filter' must be a placeholder pipeline expression which " +
            "starts with the placeholder to filter and ends with its only fn:filter stage, e.g. " +
            "header:ditto-originator|fn:filter('ne','some:subject'); further conditions are combined (AND) by " +
            "repeating the 'fn-filter' parameter; RQL expressions belong in the 'filter' parameter.";

    /**
     * The stages which, appended to a placeholder, publish when that placeholder does not resolve - what
     * {@code 'exists'} with a compared value other than {@code 'true'} cannot express in an {@code fn-filter}.
     */
    private static final String PUBLISH_WHEN_UNRESOLVED_STAGES = "|fn:default('<none>')|fn:filter('eq','<none>')";

    private static final String PUBLISH_WHEN_UNRESOLVED_HINT = "To publish when a placeholder does not resolve, " +
            "supply a value for that case and filter on it: <placeholder>" + PUBLISH_WHEN_UNRESOLVED_STAGES + ".";

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
     * All placeholders resolve to a dummy value (the empty string) and placeholder values are never resolved. The
     * resolver is immutable, so one instance is shared by all validation calls.
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
    static boolean isFunctionExpression(final String expression) {
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
     * {@code false} if it stays unresolved or is deleted (the topic should be suppressed). An expression which
     * {@link #validateFnFilter(String, DittoHeaders)} would reject is evaluated as it is: a leading
     * {@code fn:filter(...)} never resolves, whereas e.g. a leading {@code fn:default(...)} does.
     * @throws RuntimeException if the expression is malformed or cannot be evaluated - a
     * {@link DittoRuntimeException} from the pipeline grammar, or a plain runtime exception thrown while a
     * placeholder resolves its value (e.g. an {@link IllegalArgumentException} for a placeholder without a name).
     * Callers must catch {@code RuntimeException} and treat it as a non-match.
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
     * placeholder/pipeline function error is rejected. An expression starting with a function, which the resolver
     * itself would accept, is rejected up front. The resolver's pipeline grammar then enforces the structure
     * (quote-aware stage splitting, a known leading placeholder, every further stage a known function with a valid
     * signature, at most 10 function stages). Only thrown errors are checked - the resolved result is meaningless
     * in validation mode, where every placeholder resolves to a dummy value. Additionally, a leading placeholder
     * without a name (e.g. {@code header:}) is rejected, because it passes the grammar but cannot be evaluated; a
     * leading placeholder with whitespace next to its colon (e.g. {@code header: x}), because the grammar lets it
     * pass for placeholders which accept any name, but the whitespace is not dropped when the value is looked up,
     * so that a header is never found; and a further stage without the {@code fn:} prefix (e.g. {@code lower()}),
     * because the grammar takes the prefix as optional but no function is applied without it, so that the stage
     * never resolves.
     * <p>
     * Beyond the grammar, the expression must have the shape described in the {@link TargetTopicFilter class
     * documentation}: its only {@code fn:filter} stage is the last stage and has the form
     * {@code fn:filter('<rqlFunction>',<comparedValue>)} with a known literal {@code rqlFunction} - which
     * guarantees that a placeholder which does not resolve suppresses the topic instead of being filtered as the
     * empty value - and it contains no {@code fn:delete()}.
     *
     * @param fnFilter the raw {@code fn-filter} parameter value to validate.
     * @param dittoHeaders the headers of the command which triggered the validation, stamped onto the thrown
     * exception for correlation.
     * @throws ConnectionConfigurationInvalidException if the expression is invalid, e.g. because it is empty, is an
     * RQL expression, starts with a function, references an unknown placeholder or function, starts with a
     * placeholder without a name or with whitespace next to its colon, has a stage without the {@code fn:} prefix,
     * has an invalid function signature, exceeds the maximum number of pipeline stages, contains an
     * {@code fn:delete()} stage, does not end with its only {@code fn:filter} stage, or that stage is not of the
     * supported form.
     */
    public static void validateFnFilter(final String fnFilter, final DittoHeaders dittoHeaders) {
        final String pipelineExpression = fnFilter.trim();
        if (isFunctionExpression(pipelineExpression)) {
            throw invalidFnFilter(fnFilter, "The expression must start with a placeholder, the value to filter, " +
                    "not with a function: without a leading placeholder the pipeline has no value to filter." +
                    (pipelineExpression.startsWith(FILTER_STAGE_START)
                            ? " A leading fn:filter(...) stage never resolves, whatever its parameters."
                            : "") +
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
        final Matcher prefixAndColon = PLACEHOLDER_PREFIX_AND_COLON.matcher(leadingStage);
        if (prefixAndColon.find() && !":".equals(prefixAndColon.group(2))) {
            throw invalidFnFilter(fnFilter, "The leading placeholder '" + leadingStage + "' contains whitespace " +
                    "next to its ':', with which e.g. a header is never found - remove the whitespace, here '" +
                    prefixAndColon.group(1) + ":" + leadingStage.substring(prefixAndColon.end()) + "'.", null,
                    dittoHeaders);
        }
        for (final String stage : stages.subList(1, stages.size())) {
            if (!stage.startsWith(FN_PREFIX)) {
                final String prefixedStage = FN_PREFIX + stage;
                throw invalidFnFilter(fnFilter, "The stage '" + stage + "' lacks the 'fn:' prefix, without which no " +
                        "function is applied and the stage never resolves - every stage after the leading " +
                        "placeholder must be a function call starting with 'fn:'" +
                        (DELETE_STAGE.matcher(prefixedStage).find()
                                ? "; an fn:delete() stage is rejected as well, so remove the stage."
                                : ", here '" + prefixedStage + "'."), null, dittoHeaders);
            }
        }
        for (final String stage : stages) {
            if (DELETE_STAGE.matcher(stage).find()) {
                throw invalidFnFilter(fnFilter, "The stage '" + stage + "' would make the topic never publish - " +
                        "no later stage can resolve a deleted pipeline again; remove the stage.", null,
                        dittoHeaders);
            }
        }
        final String lastStage = stages.get(stages.size() - 1);
        final Matcher filterStage = FILTER_STAGE.matcher(lastStage);
        if (!filterStage.matches()) {
            throw invalidFnFilter(fnFilter, "The last stage '" + lastStage + "' is not a filter stage - the last " +
                    "stage must be fn:filter(...), the stage that yields the publish decision. A bare placeholder " +
                    "does not filter anything: to publish whenever it resolves to a non-empty value, say so " +
                    "explicitly with <placeholder>|fn:filter('exists','true'), e.g. header:ditto-originator|" +
                    "fn:filter('exists','true'); a trailing value-producing stage such as fn:upper() cannot change " +
                    "the preceding decision and a trailing fn:default(...) would discard it.", null, dittoHeaders);
        }
        for (final String stage : stages.subList(0, stages.size() - 1)) {
            if (FILTER_STAGE.matcher(stage).matches()) {
                throw invalidFnFilter(fnFilter, "The stage '" + stage + "' is not the last stage, but only one " +
                        "fn:filter stage is supported per expression - to combine several conditions (AND) " +
                        "repeat the 'fn-filter' parameter, e.g. ?fn-filter=header:ditto-originator|fn:filter(" +
                        "'ne','some:subject')&fn-filter=topic:action|fn:filter('ne','deleted').",
                        null, dittoHeaders);
            }
        }
        validateFilterStage(fnFilter, lastStage, splitQuoteAware(FUNCTION_PARAMETER, filterStage.group(1)),
                dittoHeaders);
    }

    /**
     * Checks whether the given expression passes {@link #validateFnFilter(String, DittoHeaders)}. Used to quote an
     * expression in an error text only if it would be accepted.
     *
     * @param fnFilter the raw {@code fn-filter} parameter value to check.
     * @return {@code true} if the expression is a valid {@code fn-filter}.
     */
    public static boolean isValidFnFilter(final String fnFilter) {
        try {
            validateFnFilter(fnFilter, DittoHeaders.empty());
            return true;
        } catch (final ConnectionConfigurationInvalidException e) {
            return false;
        }
    }

    /**
     * Accepts only {@code fn:filter('<rqlFunction>',<comparedValue>)} - the 2-parameter form which filters the
     * pipeline's own value - with a known literal {@code rqlFunction}. A filter value passed as parameter (the
     * 3-parameter form and {@code fn:filter(<filterValue>,'exists')}) is either a placeholder, filtered as the empty
     * value when it does not resolve, or a constant which never looks at the signal; a placeholder-valued
     * {@code rqlFunction} cannot be told apart from such a filter value. Also rejects the constant compared value
     * {@code 'exists'}, which {@code fn:filter} takes for its {@code fn:filter(<filterValue>,'exists')} form, and
     * the {@code rqlFunction} {@code 'exists'} with a compared value other than the constant {@code 'true'}
     * (case-insensitive): the filtered value is a resolved one, so {@code 'exists'} compared with anything but
     * {@code 'true'} could only match an empty value.
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
                    "resolved value. To publish whenever the placeholder resolves to a non-empty value use " +
                    "fn:filter('exists','true').", null, dittoHeaders);
        }
        if (EXISTS_FUNCTION_NAME.equals(rqlFunction) && !comparedValueIsConstant) {
            throw invalidFnFilter(fnFilter, "The stage '" + stage + "' compares 'exists' with the placeholder " +
                    comparedValueParam + ", but the compared value of 'exists' must be the constant 'true': the " +
                    "filtered value is the resolved value of the preceding pipeline, so with any other compared " +
                    "value the stage could only ever match an empty value. " + PUBLISH_WHEN_UNRESOLVED_HINT, null,
                    dittoHeaders);
        }
        if (EXISTS_FUNCTION_NAME.equals(rqlFunction) && !Boolean.parseBoolean(unquote(comparedValueParam))) {
            throw invalidFnFilter(fnFilter, "The stage '" + stage + "' could only ever match an empty value: the " +
                    "filtered value is the resolved value of the preceding pipeline, so a placeholder which does " +
                    "not resolve never reaches the stage. " + PUBLISH_WHEN_UNRESOLVED_HINT, null, dittoHeaders);
        }
    }

    /**
     * For the simple function-first expressions {@code fn:filter(<placeholder>,'<rqlFunction>',<comparedValue>)} and
     * {@code fn:filter(<placeholder>,'exists')} the corresponding placeholder-first expression, as a sentence to
     * append to the error message - or the empty string if the expression is not of these forms or the
     * placeholder-first expression is itself rejected by {@link #validateFnFilter(String, DittoHeaders)}. For
     * {@code 'exists'} with a constant compared value other than {@code 'true'} the expression which publishes when
     * the placeholder does not resolve is suggested.
     */
    private static String suggestPlaceholderFirstRewrite(final String pipelineExpression) {
        final Matcher filterStage = FILTER_STAGE.matcher(pipelineExpression);
        if (!filterStage.matches() || splitQuoteAware(PIPELINE_STAGE, pipelineExpression).size() != 1) {
            return "";
        }
        final List<String> params = splitQuoteAware(FUNCTION_PARAMETER, filterStage.group(1));
        if (params.size() < 2 || params.size() > 3 || isQuotedConstant(params.get(0)) ||
                isFunctionExpression(params.get(0)) || !isQuotedConstant(params.get(1))) {
            return "";
        }
        final String placeholder = params.get(0);
        final boolean existsFunction = EXISTS_FUNCTION_NAME.equals(unquote(params.get(1)));
        final boolean publishWhenUnresolved = params.size() == 3 && existsFunction &&
                isQuotedConstant(params.get(2)) && !Boolean.parseBoolean(unquote(params.get(2)));
        final String rewrite;
        if (params.size() == 2) {
            if (!existsFunction) {
                return "";
            }
            rewrite = placeholder + "|fn:filter(" + params.get(1) + ",'true')";
        } else if (publishWhenUnresolved) {
            rewrite = placeholder + PUBLISH_WHEN_UNRESOLVED_STAGES;
        } else {
            rewrite = placeholder + "|fn:filter(" + params.get(1) + "," + params.get(2) + ")";
        }
        if (!isValidFnFilter(rewrite)) {
            return "";
        }
        return (publishWhenUnresolved ? " To publish when " + placeholder + " does not resolve write " : " Write ") +
                rewrite + " instead.";
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
