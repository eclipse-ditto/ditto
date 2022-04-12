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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.common.Placeholders;

/**
 * A filter implementation to replace defined placeholders with their values.
 */
public final class PlaceholderFilter {

    /**
     * Apply {@link HeadersPlaceholder}s to the passed {@code authorizationContext} and return an
     * {@link AuthorizationContext} where the placeholders were replaced.
     *
     * @param authorizationContext authorizationContext to apply placeholder substitution in.
     * @param headers the headers to apply HeadersPlaceholder substitution with.
     * @return AuthorizationContext as result of placeholder substitution.
     * @throws UnresolvedPlaceholderException if not all placeholders could be resolved
     */
    public static AuthorizationContext applyHeadersPlaceholderToAuthContext(
            final AuthorizationContext authorizationContext,
            final Map<String, String> headers) {

        // check if we have to replace anything at all
        if (authorizationContext.stream()
                .map(AuthorizationSubject::getId)
                .noneMatch(Placeholders::containsAnyPlaceholder)) {
            return authorizationContext;
        }

        final HeadersPlaceholder headersPlaceholder = PlaceholderFactory.newHeadersPlaceholder();
        final List<AuthorizationSubject> subjects = authorizationContext.stream()
                .map(AuthorizationSubject::getId)
                .flatMap(
                        id -> applyForAll(id,
                                PlaceholderFactory.newExpressionResolver(headersPlaceholder, headers)).stream())
                .map(AuthorizationModelFactory::newAuthSubject)
                .collect(Collectors.toList());
        return AuthorizationModelFactory.newAuthContext(authorizationContext.getType(), subjects);
    }

    /**
     * Finds all placeholders ({@code {{ ... }}}) defined in the given {@code template} and tries to replace them
     * by applying the given {@code placeholder}.
     *
     * @param template the template to replace placeholders and optionally apply pipeline stages (functions) in.
     * @param placeholderSource the source to resolve {@code placeholder} names from.
     * @param placeholder the placeholder used to resolve placeholders.
     * @param <T> the type of the placeholderSource
     * @throws UnresolvedPlaceholderException if not all placeholders could be resolved
     */
    public static <T> String apply(final String template, final T placeholderSource, final Placeholder<T> placeholder) {
        return apply(template, PlaceholderFactory.newExpressionResolver(placeholder, placeholderSource));
    }

    /**
     * Finds all placeholders ({@code {{ ... }}}) defined in the given {@code template} and tries to replace them
     * by applying the given {@code placeholder}.
     *
     * @param template the template to replace placeholders and optionally apply pipeline stages (functions) in.
     * @param placeholderSource the source to resolve {@code placeholder} names from.
     * @param placeholder the placeholder used to resolve placeholders.
     * @param <T> the type of the placeholderSource
     * @throws UnresolvedPlaceholderException if not all placeholders could be resolved
     * @return the template string with all resolved values.
     * @since 2.4.0
     */
    public static <T> List<String> applyForAll(final String template, final T placeholderSource,
            final Placeholder<T> placeholder) {
        return applyForAll(template, PlaceholderFactory.newExpressionResolver(placeholder, placeholderSource));
    }

    /**
     * Finds all placeholders ({@code {{ ... }}}) defined in the given {@code template} and tries to replace them
     * by applying the given {@code expressionResolver}.
     *
     * @param template the template string.
     * @param expressionResolver the expressionResolver used to resolve placeholders and optionally pipeline stages
     * (functions).
     * @return the template string with the first resolved value
     * @throws UnresolvedPlaceholderException if not all placeholders could be resolved
     */
    public static String apply(final String template, final ExpressionResolver expressionResolver) {
        return applyForAll(template, expressionResolver)
                .stream()
                .findFirst()
                .orElseThrow(() -> UnresolvedPlaceholderException.newBuilder(template).build());
    }

    /**
     * Finds all placeholders ({@code {{ ... }}}) defined in the given {@code template} and tries to replace them
     * by applying the given {@code expressionResolver}.
     *
     * @param template the template string.
     * @param expressionResolver the expressionResolver used to resolve placeholders and optionally pipeline stages
     * (functions).
     * @return the template string with all resolved values
     * @throws UnresolvedPlaceholderException if not all placeholders could be resolved
     * @since 2.4.0
     */
    public static List<String> applyForAll(final String template, final ExpressionResolver expressionResolver) {
        return doApply(template, expressionResolver);
    }

    /**
     * Finds all placeholders ({@code {{ ... }}}) defined in the given {@code template} and tries to replace them
     * by applying the given {@code expressionResolver}. If a pipeline function deletes the element or the pipeline
     * leads to an unresolved element, then return an empty optional.
     *
     * @param template the template string.
     * @param resolver the expression-resolver used to resolve placeholders and optionally pipeline stages
     * (functions).
     * @return the first template string if resolution succeeds with a result,
     * or an empty optional if the template string fails to resolve or is deleted.
     * @throws UnresolvedPlaceholderException in case the template's placeholders could not completely be resolved
     * @throws PlaceholderFunctionTooComplexException thrown if the {@code template} contains a placeholder
     * function chain which is too complex (e.g. too much chained function calls)
     */
    public static Optional<String> applyOrElseDelete(final String template, final ExpressionResolver resolver) {
        return resolver.resolve(template).findFirst();
    }

    /**
     * Finds all placeholders ({@code {{ ... }}}) defined in the given {@code template} and tries to replace them
     * by applying the given {@code expressionResolver}. If a pipeline function deletes the element or if a placeholder
     * fails to resolve then return the original string.
     *
     * @param template the template string.
     * @param resolver the expression-resolver used to resolve placeholders and optionally pipeline stages
     * (functions).
     * @return a template string if resolution succeeds with a result,
     * or an empty optional if the template string is deleted.
     * @throws UnresolvedPlaceholderException in case the template's placeholders could not completely be resolved
     * @throws PlaceholderFunctionTooComplexException thrown if the {@code template} contains a placeholder
     * function chain which is too complex (e.g. too much chained function calls)
     */
    public static String applyOrElseRetain(final String template, final ExpressionResolver resolver) {
        return resolver.resolve(template).findFirst().orElse(template);
    }

    /**
     * Validates that the passed {@code template} is valid and that the placeholders in the passed {@code template}
     * are completely replaceable by the provided {@code placeholders}.
     *
     * @param template a string potentially containing placeholders to replace
     * @param placeholders the {@link Placeholder}s to use for replacement
     * @throws UnresolvedPlaceholderException in case the template's placeholders could not completely be resolved
     * @throws PlaceholderFunctionTooComplexException thrown if the {@code template} contains a placeholder
     * function chain which is too complex (e.g. too much chained function calls)
     */
    public static void validate(final String template, final Placeholder<?>... placeholders) {
        final ExpressionResolver resolver = PlaceholderFactory.newExpressionResolverForValidation(placeholders);
        resolver.resolve(template);
    }

    /**
     * Validates that the passed {@code template} is valid and that the placeholders in the passed {@code template}
     * are completely replaceable by the provided {@code placeholders}. Each placeholder will be replaced by
     * {@code stringUsedInPlaceholderReplacement} and the resolved template without any remaining placeholders will be returned.
     *
     * @param template a string potentially containing placeholders to replace
     * @param stringUsedInPlaceholderReplacement the dummy value used as a replacement for the found placeholders.
     * @param placeholders the {@link Placeholder}s to use for replacement
     * @return the {@code template} with every placeholder replaced by {@code stringUsedInPlaceholderReplacement} in all variants.
     * @throws UnresolvedPlaceholderException in case the template's placeholders could not completely be resolved
     * @throws PlaceholderFunctionTooComplexException thrown if the {@code template} contains a placeholder
     * function chain which is too complex (e.g. too much chained function calls)
     * @since 2.4.0
     */
    public static List<String> validateAndReplaceAll(final String template,
            final String stringUsedInPlaceholderReplacement,
            final Placeholder<?>... placeholders) {
        return doApply(template,
                PlaceholderFactory.newExpressionResolverForValidation(stringUsedInPlaceholderReplacement, placeholders)
        );
    }

    /**
     * Validates that the passed {@code template} is valid and that the placeholders in the passed {@code template}
     * are completely replaceable by the provided {@code placeholders}. Each placeholder will be replaced by
     * {@code stringUsedInPlaceholderReplacement} and the resolved template without any remaining placeholders will be returned.
     *
     * @param template a string potentially containing placeholders to replace
     * @param stringUsedInPlaceholderReplacement the dummy value used as a replacement for the found placeholders.
     * @param placeholders the {@link Placeholder}s to use for replacement
     * @return the {@code template} with every placeholder replaced by {@code stringUsedInPlaceholderReplacement} with
     * only the first variant.
     * @throws UnresolvedPlaceholderException in case the template's placeholders could not completely be resolved
     * @throws PlaceholderFunctionTooComplexException thrown if the {@code template} contains a placeholder
     * function chain which is too complex (e.g. too much chained function calls)
     */
    public static String validateAndReplace(final String template,
            final String stringUsedInPlaceholderReplacement,
            final Placeholder<?>... placeholders) {
        return apply(template,
                PlaceholderFactory.newExpressionResolverForValidation(stringUsedInPlaceholderReplacement, placeholders)
        );
    }

    private static List<String> doApply(final String template, final ExpressionResolver expressionResolver) {
        final Supplier<String> throwUnresolvedPlaceholderException = () -> {
            throw UnresolvedPlaceholderException.newBuilder(template).build();
        };
        return expressionResolver.resolve(template)
                .evaluate(PipelineElement.<String>newVisitorBuilder()
                        .resolved(Function.identity())
                        .unresolved(throwUnresolvedPlaceholderException)
                        .deleted(throwUnresolvedPlaceholderException)
                        .build());
    }

    private PlaceholderFilter() {
        throw new AssertionError();
    }

}
