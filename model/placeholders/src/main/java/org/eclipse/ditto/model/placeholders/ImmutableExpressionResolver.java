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
package org.eclipse.ditto.model.placeholders;

import static org.eclipse.ditto.model.placeholders.Expression.SEPARATOR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;

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

    private static final String DEFAULT_VALIDATION_STRING_ID = "justForValidation_" + UUID.randomUUID().toString();
    private final String placeholderReplacementInValidation;

    private final List<PlaceholderResolver<?>> placeholderResolvers;

    ImmutableExpressionResolver(final List<PlaceholderResolver<?>> placeholderResolvers) {
        this(placeholderResolvers, DEFAULT_VALIDATION_STRING_ID);
    }

    ImmutableExpressionResolver(final List<PlaceholderResolver<?>> placeholderResolvers,
            final String stringUsedInPlaceholderValidation) {
        this.placeholderResolvers = Collections.unmodifiableList(new ArrayList<>(placeholderResolvers));
        this.placeholderReplacementInValidation = stringUsedInPlaceholderValidation;
    }

    @Override
    public PipelineElement resolveAsPipelineElement(final String placeholderExpression) {
        final List<String> pipelineStagesExpressions = getPipelineStagesExpressions(placeholderExpression);
        final String firstPlaceholderInPipe = getFirstExpressionInPipe(pipelineStagesExpressions);
        if (isFirstPlaceholderFunction(firstPlaceholderInPipe)) {
            return getPipelineFromExpressions(pipelineStagesExpressions, 0).execute(PipelineElement.unresolved(), this);
        } else {
            final PipelineElement pipelineInput = resolvePlaceholderWithOrWithoutPrefix(firstPlaceholderInPipe);
            return getPipelineFromExpressions(pipelineStagesExpressions, 1).execute(pipelineInput, this);
        }
    }

    private PipelineElement resolvePlaceholderWithOrWithoutPrefix(final String placeholderInPipeline) {
        return placeholderResolvers.stream()
                .flatMap(resolver ->
                        resolvePlaceholderWithoutPrefixIfSupported(resolver, placeholderInPipeline)
                                .flatMap(p -> resolvePlaceholder(resolver, p))
                                .map(Stream::of)
                                .orElseGet(Stream::empty)
                )
                .findAny()
                .map(PipelineElement::resolved)
                .orElseGet(PipelineElement::unresolved);
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

    private Optional<String> resolvePlaceholderWithoutPrefixIfSupported(final PlaceholderResolver<?> resolver,
            final String placeholder) {
        final int separatorIndex = placeholder.indexOf(SEPARATOR);
        if (separatorIndex == -1) {
            throw UnresolvedPlaceholderException.newBuilder(placeholder).build();
        }
        final String prefix = placeholder.substring(0, separatorIndex).trim();
        if (prefix.equals(resolver.getPrefix())) {
            return Optional.of(placeholder.substring(separatorIndex + 1).trim());
        }
        return Optional.empty();
    }

    private Optional<String> resolvePlaceholder(final PlaceholderResolver<?> resolver,
            final String placeholderWithoutPrefix) {
        return Optional.of(resolver)
                .filter(p -> p.supports(placeholderWithoutPrefix))
                .flatMap(p -> {
                    if (resolver.isForValidation()) {
                        return Optional.of(placeholderReplacementInValidation);
                    } else {
                        return resolver.resolve(placeholderWithoutPrefix);
                    }
                });
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
                ", placeholderReplacementInValidation=" + placeholderReplacementInValidation +
                ", placeholderResolvers=" + placeholderResolvers +
                "]";
    }

    private static boolean isFirstPlaceholderFunction(final String firstPlaceholderInPipeline) {
        return firstPlaceholderInPipeline.startsWith(FunctionExpression.PREFIX + SEPARATOR);
    }
}
