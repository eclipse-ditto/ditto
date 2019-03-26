/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.Placeholders;
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
    public String resolve(final String expressionTemplate, final boolean allowUnresolved) {

        String templateInWork = expressionTemplate;
        int placeholdersIdx = 0;
        while (Placeholders.containsAnyPlaceholder(templateInWork) && placeholdersIdx < placeholderResolvers.size()) {
            final PlaceholderResolver<?> placeholderResolver = placeholderResolvers.get(placeholdersIdx);

            final Function<String, Optional<String>> placeholderReplacerFunction =
                    makePlaceholderReplacerFunction(placeholderResolver);

            placeholdersIdx++;
            final boolean isLastPlaceholderResolver = placeholdersIdx == placeholderResolvers.size();
            templateInWork = Placeholders.substitute(templateInWork, placeholderReplacerFunction,
                    UNRESOLVED_INPUT_HANDLER, !isLastPlaceholderResolver || allowUnresolved);
        }

        return templateInWork;
    }

    @Override
    public Optional<String> resolveSinglePlaceholder(final String placeholder) {

        for (final PlaceholderResolver<?> resolver : placeholderResolvers) {

            final Optional<String> resolvedOpt = makePlaceholderReplacerFunction(resolver).apply(placeholder);
            if (resolvedOpt.isPresent()) {
                return resolvedOpt;
            }
        }

        return Optional.empty();
    }

    private Function<String, Optional<String>> makePlaceholderReplacerFunction(
            final PlaceholderResolver<?> placeholderResolver) {

        return template -> {

            final List<String> pipelineStagesExpressions = getPipelineStagesExpressions(template);
            final String placeholderTemplate = getFirstPlaceholderInPipe(pipelineStagesExpressions);
            final Pipeline pipeline = getPipelineFromExpressions(pipelineStagesExpressions);

            final Optional<String> placeholderWithoutPrefix =
                    resolvePlaceholderWithoutPrefixIfSupported(placeholderResolver, placeholderTemplate);
            return placeholderWithoutPrefix
                    .map(p -> resolvePlaceholder(placeholderResolver, p))
                    .flatMap(pipelineInput -> {
                        if (Optional.of(placeholderReplacementInValidation).equals(pipelineInput)) {
                            pipeline.validate();
                            // let the input pass if validation succeeded:
                            return pipelineInput;
                        }
                        return pipeline.execute(pipelineInput, this);
                    });

        };
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

    private String getFirstPlaceholderInPipe(final List<String> pipelineStagesExpressions) {
        if (pipelineStagesExpressions.isEmpty()) {
            return "";
        }
        return pipelineStagesExpressions.get(0); // the first pipeline stage has to start with a placeholder
    }

    private Pipeline getPipelineFromExpressions(final List<String> pipelineStagesExpressions) {
        final List<String> pipelineStages = pipelineStagesExpressions.stream()
                .skip(1) // ignore first, as the first one is a placeholder that will be used as the input for the pipeline
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

}
