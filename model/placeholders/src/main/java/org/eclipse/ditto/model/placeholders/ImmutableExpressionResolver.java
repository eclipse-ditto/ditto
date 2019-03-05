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
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.Placeholders;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;

/**
 * Immutable implementation of {@link ExpressionResolver} containing the logic of how an expression is resolved.
 */
@Immutable
final class ImmutableExpressionResolver implements ExpressionResolver {

    private static final String PLACEHOLDER_BASIC = "[\\w\\-_]+:[\\w\\-_|]+";
    private static final String ANY_NUMBER_OF_SPACES = "\\s*";
    private static final String OR = "|";
    private static final String STRING_WITH_SINGLE_QUOTES =
            "(\\s*(?<!('\\s))'(?=([\\w\\-_|:\\s]*'))[\\w\\-_|:\\s]*'?\\s*)";
    private static final String STRING_WITH_DOUBLE_QUOTES =
            "(\\s*(?<!(\"\\s))\"(?=([\\w\\-_|:\\s]*\"))[\\w\\-_|:\\s]*\"?\\s*)";
    private static final String EMPTY_FUNCTION = "";
    private static final String COMMA_IN_BETWEEN_PARAMETERS = "(?<!(\\(\\s)),?";

    private static final String PIPE_PATTERN_STR = PLACEHOLDER_BASIC
            + ANY_NUMBER_OF_SPACES
            + "(\\(" // Open function
            + ANY_NUMBER_OF_SPACES
            + "(" // Repeating parameters group
            + "(" // OR group
            + STRING_WITH_DOUBLE_QUOTES + OR
            + STRING_WITH_SINGLE_QUOTES + OR
            + EMPTY_FUNCTION + OR
            + "(\\s*" + PLACEHOLDER_BASIC + ")"
            + ")" // End OR group
            + ANY_NUMBER_OF_SPACES
            + COMMA_IN_BETWEEN_PARAMETERS
            + ANY_NUMBER_OF_SPACES
            + ")*" + ANY_NUMBER_OF_SPACES + "\\)" // Closing parameters group and function
            + ANY_NUMBER_OF_SPACES
            + ")?";

    private static final Pattern PIPE_PATTERN = Pattern.compile(PIPE_PATTERN_STR);

    private static final Function<String, DittoRuntimeException> UNRESOLVED_INPUT_HANDLER = unresolvedInput ->
            UnresolvedPlaceholderException.newBuilder(unresolvedInput).build();

    private final List<PlaceholderResolver<?>> placeholderResolvers;

    ImmutableExpressionResolver(final List<PlaceholderResolver<?>> placeholderResolvers) {
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

            final Optional<String> placeholderWithoutPrefix = resolvePlaceholderWithoutPrefixIfSupported(placeholderResolver, placeholderTemplate);
            return placeholderWithoutPrefix
                    .map(p -> resolvePlaceholder(placeholderResolver, p))
                    .flatMap(pipelineInput -> pipeline.execute(pipelineInput, this));

        };
    }

    private List<String> getPipelineStagesExpressions(final String template) {
        final Matcher matcher = PIPE_PATTERN.matcher(template);

        final List<String> pipelineStagesExpressions = new ArrayList<>();

        while (matcher.find()) {
            pipelineStagesExpressions.add(matcher.group().trim());
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

    private Optional<String> resolvePlaceholderWithoutPrefixIfSupported(final PlaceholderResolver<?> resolver, final String placeholder) {
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

    private Optional<String> resolvePlaceholder(final PlaceholderResolver<?> resolver, final String placeholderWithoutPrefix) {
        return Optional.of(resolver)
                .filter(p -> p.supports(placeholderWithoutPrefix))
                .flatMap(p -> resolver.resolve(placeholderWithoutPrefix));
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
