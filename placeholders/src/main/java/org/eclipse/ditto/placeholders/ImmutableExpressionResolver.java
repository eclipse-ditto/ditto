/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.placeholders;

import static org.eclipse.ditto.placeholders.Expression.SEPARATOR;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;

/**
 * Immutable implementation of {@link ExpressionResolver} containing the logic of how an expression is resolved.
 */
@Immutable
final class ImmutableExpressionResolver implements ExpressionResolver {

    private static final int MAX_COUNT_PIPELINE_FUNCTIONS = 10;

    private static final String OR = "|";

    private static final String NO_QUOTE = "[^|'\"]++";

    private static final String SINGLE_QUOTED_STRING =
            String.format("'%s'", PipelineFunction.SINGLE_QUOTED_STRING_CONTENT);

    private static final String DOUBLE_QUOTED_STRING =
            String.format("\"%s\"", PipelineFunction.DOUBLE_QUOTED_STRING_CONTENT);

    private static final String PIPE_STAGE =
            "(?:" + NO_QUOTE + OR + SINGLE_QUOTED_STRING + OR + DOUBLE_QUOTED_STRING + ")++";

    private static final Pattern PIPE_STAGE_PATTERN = Pattern.compile(PIPE_STAGE);

    private static final String PIPE_PATTERN_STR = PIPE_STAGE + "(?:\\|" + PIPE_STAGE + ")*+";

    private static final Pattern PIPE_PATTERN = Pattern.compile(PIPE_PATTERN_STR);

    private static final Function<String, DittoRuntimeException> UNRESOLVED_INPUT_HANDLER = unresolvedInput ->
            UnresolvedPlaceholderException.newBuilder(unresolvedInput).build();

    @Nullable private final String placeholderReplacementInValidation;

    private final Map<String, PlaceholderResolver<?>> placeholderResolvers;

    ImmutableExpressionResolver(final List<PlaceholderResolver<?>> placeholderResolvers) {
        this(placeholderResolvers, null);
    }

    ImmutableExpressionResolver(final List<PlaceholderResolver<?>> placeholderResolvers,
            @Nullable final String stringUsedInPlaceholderValidation) {
        placeholderReplacementInValidation = stringUsedInPlaceholderValidation;
        this.placeholderResolvers = Collections.unmodifiableMap(placeholderResolvers.stream()
                .collect(Collectors.toMap(PlaceholderResolver::getPrefix, Function.identity()))
        );
    }

    @Override
    public PipelineElement resolveAsPipelineElement(final String placeholderExpression) {
        final List<String> pipelineStagesExpressions = getPipelineStagesExpressions(placeholderExpression);
        final String firstPlaceholderInPipe = getFirstExpressionInPipe(pipelineStagesExpressions);
        if (isFirstPlaceholderFunction(firstPlaceholderInPipe)) {
            return getPipelineFromExpressions(pipelineStagesExpressions, 0).execute(PipelineElement.unresolved(), this);
        } else {
            final PipelineElement pipelineInput = resolveSinglePlaceholder(firstPlaceholderInPipe);
            return getPipelineFromExpressions(pipelineStagesExpressions, 1).execute(pipelineInput, this);
        }
    }

    private Optional<Map.Entry<PlaceholderResolver<?>, String>> findPlaceholderResolver(
            final String placeholderInPipeline) {
        return getPlaceholderPrefix(placeholderInPipeline)
                .flatMap(prefix -> {
                    final String name = placeholderInPipeline.substring(prefix.length() + 1);
                    return Optional.ofNullable(placeholderResolvers.get(prefix))
                            .filter(resolver -> resolver.supports(name))
                            .map(resolver -> new AbstractMap.SimpleImmutableEntry<>(resolver, name));
                });
    }

    private PipelineElement resolveSinglePlaceholder(final String placeholderInPipeline) {
        final Map.Entry<PlaceholderResolver<?>, String> resolverPair = findPlaceholderResolver(placeholderInPipeline)
                .orElseThrow(() -> UnresolvedPlaceholderException.newBuilder(placeholderInPipeline).build());

        if (placeholderReplacementInValidation == null) {
            // normal mode
            final List<String> resolvedValues = resolverPair.getKey().resolveValues(resolverPair.getValue());
            return PipelineElement.resolved(resolvedValues);
        } else {
            // validation mode: all placeholders resolve to dummy value.
            return PipelineElement.resolved(placeholderReplacementInValidation);
        }
    }

    private List<String> getPipelineStagesExpressions(final String template) {

        if (!PIPE_PATTERN.matcher(template).matches()) {
            throw UNRESOLVED_INPUT_HANDLER.apply(template);
        }

        final List<String> pipelineStagesExpressions = new ArrayList<>();
        final Matcher matcher = PIPE_STAGE_PATTERN.matcher(template);

        while (matcher.find()) {
            pipelineStagesExpressions.add(matcher.group().trim());

            // +1 for the starting placeholder
            if (pipelineStagesExpressions.size() > MAX_COUNT_PIPELINE_FUNCTIONS + 1) {
                throw PlaceholderFunctionTooComplexException.newBuilder(MAX_COUNT_PIPELINE_FUNCTIONS).build();
            }
        }
        return pipelineStagesExpressions;
    }

    // the first expression can be a placeholder or a function expression
    private String getFirstExpressionInPipe(final List<String> pipelineStagesExpressions) {
        if (pipelineStagesExpressions.isEmpty()) {
            return "";
        }
        return pipelineStagesExpressions.get(0);
    }

    private Pipeline getPipelineFromExpressions(final List<String> pipelineStagesExpressions, final int skip) {
        final List<String> pipelineStages = pipelineStagesExpressions.stream()
                .skip(skip) // ignore pre-processed expressions
                .collect(Collectors.toList());
        return new ImmutablePipeline(ImmutableFunctionExpression.INSTANCE, pipelineStages);
    }

    private Optional<String> getPlaceholderPrefix(final String placeholder) {
        final int separatorIndex = placeholder.indexOf(SEPARATOR);
        if (separatorIndex == -1) {
            return Optional.empty();
        }
        return Optional.of(placeholder.substring(0, separatorIndex).trim());
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableExpressionResolver)) {
            return false;
        }
        final ImmutableExpressionResolver that = (ImmutableExpressionResolver) o;
        return Objects.equals(placeholderReplacementInValidation, that.placeholderReplacementInValidation) &&
                Objects.equals(placeholderResolvers, that.placeholderResolvers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(placeholderReplacementInValidation, placeholderResolvers);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "placeholderReplacementInValidation=" + placeholderReplacementInValidation +
                ", placeholderResolvers=" + placeholderResolvers +
                "]";
    }

    private static boolean isFirstPlaceholderFunction(final String firstPlaceholderInPipeline) {
        return firstPlaceholderInPipeline.startsWith(FunctionExpression.PREFIX + SEPARATOR);
    }
}
