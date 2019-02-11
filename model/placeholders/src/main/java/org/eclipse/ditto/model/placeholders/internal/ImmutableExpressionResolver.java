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
package org.eclipse.ditto.model.placeholders.internal;

import static org.eclipse.ditto.model.placeholders.ExpressionStage.SEPARATOR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.Placeholders;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.PlaceholderResolver;

/**
 *
 */
@Immutable
final class ImmutableExpressionResolver implements ExpressionResolver {

    private static final String PIPE_PATTERN_STR = "(\\s+\\|\\s+)";
    private static final Pattern PIPE_PATTERN = Pattern.compile(PIPE_PATTERN_STR);

//    private static final String SINGLE_EXPRESSION_PATTERN = "(\\w+:(\\w|\\|)+(\\((\"([^\"]*)\"|'([^']*)'|\\w+:.+)\\))?)";
//    private static final String ANY_WHITESPACES_STR = "\\s*";
//    private static final String OVERALL_PATTERN_STR =
//                    ANY_WHITESPACES_STR +
//                    "(" + SINGLE_EXPRESSION_PATTERN + PIPE_PATTERN_STR + ")*" +
//                    SINGLE_EXPRESSION_PATTERN +
//                    ANY_WHITESPACES_STR;
//    private static final Pattern OVERALL_PATTERN = Pattern.compile(OVERALL_PATTERN_STR);

    private static final Function<String, DittoRuntimeException> UNRESOLVED_INPUT_HANDLER = unresolvedInput ->
            UnresolvedPlaceholderException.newBuilder(unresolvedInput).build();

    private final List<PlaceholderResolver<?>> placeholderResolvers;

    ImmutableExpressionResolver(
            final List<PlaceholderResolver<?>> placeholderResolvers) {
        this.placeholderResolvers = Collections.unmodifiableList(new ArrayList<>(placeholderResolvers));
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
            templateInWork = Placeholders.substitute(templateInWork, placeholderReplacerFunction,
                    UNRESOLVED_INPUT_HANDLER, placeholdersIdx < placeholderResolvers.size() || allowUnresolved);
        }

        return templateInWork;
    }

    @Override
    public String resolveSinglePlaceholder(final String placeholder, final boolean allowUnresolved) {

        for (final PlaceholderResolver<?> resolver : placeholderResolvers) {
            final Optional<String> resolvedOpt = makePlaceholderReplacerFunction(resolver).apply(placeholder);
            if (resolvedOpt.isPresent()) {
                return resolvedOpt.get();
            }
        }

        if (!allowUnresolved) {
            throw UNRESOLVED_INPUT_HANDLER.apply(placeholder);
        } else {
            return placeholder;
        }
    }

    private Function<String, Optional<String>> makePlaceholderReplacerFunction(
            final PlaceholderResolver<?> placeholderResolver) {

        return template -> {

            final List<String> pipelineStagesExpressions = PIPE_PATTERN.splitAsStream(template)
                    .map(String::trim)
                    .collect(Collectors.toList());

            final String placeholderTemplate =
                    pipelineStagesExpressions.get(0); // the first pipeline stage has to start with a placeholder

            final List<PipelineStage> pipelineStages =
                    pipelineStagesExpressions.subList(1, pipelineStagesExpressions.size()).stream()
                            .map(String::trim)
                            .map(ImmutablePipelineStage::new)
                            .collect(Collectors.toList());
            final Pipeline pipeline = new ImmutablePipeline(pipelineStages);

            final Optional<String> pipelineInput = resolvePlaceholder(placeholderResolver, placeholderTemplate);
            return pipeline.executeStages(pipelineInput, this);
        };
    }

    private Optional<String> resolvePlaceholder(final PlaceholderResolver<?> prefixed, final String placeholder) {

        final int separatorIndex = placeholder.indexOf(SEPARATOR);
        if (separatorIndex == -1) {
            throw UnresolvedPlaceholderException.newBuilder(placeholder).build();
        }
        final String prefix = placeholder.substring(0, separatorIndex).trim();
        final String placeholderWithoutPrefix = placeholder.substring(separatorIndex + 1).trim();

        return Optional.of(prefixed)
                .filter(p -> prefix.equals(p.getPrefix()))
                .filter(p -> p.supports(placeholderWithoutPrefix))
                .flatMap(p -> prefixed.resolve(placeholderWithoutPrefix));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableExpressionResolver)) {
            return false;
        }
        final ImmutableExpressionResolver that = (ImmutableExpressionResolver) o;
        return Objects.equals(placeholderResolvers, that.placeholderResolvers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(placeholderResolvers);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "placeholderResolvers=" + placeholderResolvers +
                "]";
    }
}
