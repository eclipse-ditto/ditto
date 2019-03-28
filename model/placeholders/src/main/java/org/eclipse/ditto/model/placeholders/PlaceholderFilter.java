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

import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.Placeholders;
import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;

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
    public static AuthorizationContext applyHeadersPlaceholderToAuthContext(final AuthorizationContext authorizationContext,
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
                .map(id -> apply(id, PlaceholderFactory.newExpressionResolver(headersPlaceholder, headers)))
                .map(AuthorizationModelFactory::newAuthSubject)
                .collect(Collectors.toList());
        return AuthorizationModelFactory.newAuthContext(subjects);
    }

    /**
     * Apply {@link ThingPlaceholder}s to addresses and collect the result as a map.
     *
     * @param addresses addresses to apply placeholder substitution.
     * @param thingId the thing ID.
     * @param unresolvedPlaceholderListener what to do if placeholder substitution fails.
     * @return map from successfully filtered addresses to the result of placeholder substitution.
     * @throws UnresolvedPlaceholderException if not all placeholders could be resolved
     */
    public static Map<String, String> applyThingPlaceholderToAddresses(final Collection<String> addresses, final String thingId,
            final Consumer<String> unresolvedPlaceholderListener) {

        return addresses.stream()
                .flatMap(address -> {
                    final String filteredAddress =
                            applyThingPlaceholder(address, thingId, unresolvedPlaceholderListener);
                    return filteredAddress == null
                            ? Stream.empty()
                            : Stream.of(new AbstractMap.SimpleEntry<>(address, filteredAddress));
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Nullable
    private static String applyThingPlaceholder(final String address, final String thingId,
            final Consumer<String> unresolvedPlaceholderListener) {
        try {
            return apply(address, PlaceholderFactory.newExpressionResolver(
                    PlaceholderFactory.newThingPlaceholder(), thingId));
        } catch (final UnresolvedPlaceholderException e) {
            unresolvedPlaceholderListener.accept(address);
            return null;
        }
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
    static <T> String apply(final String template, final T placeholderSource, final Placeholder<T> placeholder) {
        return apply(template, PlaceholderFactory.newExpressionResolver(placeholder, placeholderSource));
    }

    /**
     * Finds all placeholders ({@code {{ ... }}}) defined in the given {@code template} and tries to replace them
     * by applying the given {@code placeholder}.
     *
     * @param template the template to replace placeholders and optionally apply pipeline stages (functions) in.
     * @param placeholderSource the source to resolve {@code placeholder} names from.
     * @param placeholder the placeholder used to resolve placeholders.
     * @param allowUnresolved if {@code false} this method throws an exception if there are any
     * unresolved placeholders after applying the given placeholders.
     * @param <T> the type of the placeholderSource
     * @throws UnresolvedPlaceholderException if {@code allowUnresolved} was @{code false} and not all placeholders
     * could be resolved
     */
    static <T> String apply(final String template, final T placeholderSource, final Placeholder<T> placeholder,
            boolean allowUnresolved) {
        return apply(template, PlaceholderFactory.newExpressionResolver(placeholder, placeholderSource), allowUnresolved);
    }

    /**
     * Finds all placeholders ({@code {{ ... }}}) defined in the given {@code template} and tries to replace them
     * by applying the given {@code expressionResolver}.
     *
     * @param template the template string.
     * @param expressionResolver the expressionResolver used to resolve placeholders and optionally pipeline stages
     * (functions).
     * @return the template string with the resolved values
     * @throws UnresolvedPlaceholderException if not all placeholders could be resolved
     */
    public static String apply(final String template, final ExpressionResolver expressionResolver) {
        return apply(template, expressionResolver, false);
    }

    /**
     * Finds all placeholders ({@code {{ ... }}}) defined in the given {@code template} and tries to replace them
     * by applying the given {@code expressionResolver}.
     *
     * @param template the template string.
     * @param expressionResolver the expressionResolver used to resolve placeholders and optionally pipeline stages
     * (functions).
     * @param allowUnresolved if {@code false} this method throws an exception if there are any
     * unresolved placeholders after applying the given placeholders.
     * @return the template string with the resolved values
     * @throws UnresolvedPlaceholderException if {@code allowUnresolved} is true and not all
     * placeholders were resolved
     * @throws PlaceholderFunctionTooComplexException thrown if the {@code template} contains a placeholder
     * function chain which is too complex (e.g. too much chained function calls)
     */
    public static String apply(final String template, final ExpressionResolver expressionResolver,
            final boolean allowUnresolved) {

        return doApply(template, expressionResolver, allowUnresolved);
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
        String replaced = template;
        for (int i = 0; i < placeholders.length; i++) {
            boolean isNotLastPlaceholder = i < placeholders.length - 1;
            final Placeholder thePlaceholder = placeholders[i];
            final ExpressionResolver expressionResolver = PlaceholderFactory
                    .newExpressionResolverForValidation(thePlaceholder);

            replaced = doApply(replaced, expressionResolver, isNotLastPlaceholder);
        }
    }

    /**
     * Validates that the passed {@code template} is valid and that the placeholders in the passed {@code template}
     * are completely replaceable by the provided {@code placeholders}. Each placeholder will be replaced by
     * {@code stringUsedInPlaceholderReplacement} and the resolved template without any remaining placeholders will be returned.
     *
     * @param template a string potentially containing placeholders to replace
     * @param stringUsedInPlaceholderReplacement the dummy value used as a replacement for the found placeholders.
     * @param placeholders the {@link Placeholder}s to use for replacement
     * @throws UnresolvedPlaceholderException in case the template's placeholders could not completely be resolved
     * @throws PlaceholderFunctionTooComplexException thrown if the {@code template} contains a placeholder
     * function chain which is too complex (e.g. too much chained function calls)
     * @return the {@code template} with every placeholder replaced by {@code stringUsedInPlaceholderReplacement}.
     */
    public static String validateAndReplace(final String template, final String stringUsedInPlaceholderReplacement, final Placeholder<?>... placeholders) {
        String replaced = template;
        for (int i = 0; i < placeholders.length; i++) {
            boolean isNotLastPlaceholder = i < placeholders.length - 1;
            final Placeholder thePlaceholder = placeholders[i];
            final ExpressionResolver expressionResolver = PlaceholderFactory
                    .newExpressionResolverForValidation(thePlaceholder, stringUsedInPlaceholderReplacement);

            replaced = doApply(replaced, expressionResolver, isNotLastPlaceholder);
        }
        return replaced;
    }

    private static String doApply(final String template,
            final ExpressionResolver expressionResolver,
            final boolean allowUnresolved) {

        return expressionResolver.resolve(template, allowUnresolved);
    }

    static String checkAllPlaceholdersResolved(final String input) {
        if (Placeholders.containsAnyPlaceholder(input)) {
            throw UnresolvedPlaceholderException.newBuilder(input).build();
        }
        return input;
    }

    private PlaceholderFilter() {
        throw new AssertionError();
    }

}
