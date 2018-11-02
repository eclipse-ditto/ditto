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
package org.eclipse.ditto.services.models.connectivity.placeholder;

import static org.eclipse.ditto.services.models.connectivity.placeholder.Placeholder.SEPARATOR;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.Placeholders;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;

/**
 * A filter implementation to replace defined placeholders with their values.
 */
public final class PlaceholderFilter {

    private static final Function<String, DittoRuntimeException> UNRESOLVED_INPUT_HANDLER = unresolvedInput ->
            UnresolvedPlaceholderException.newBuilder(unresolvedInput).build();

    /**
     * Apply {@link HeadersPlaceholder}s to the passed {@code authorizationContext} and return an
     * {@link AuthorizationContext} where the placeholders were replaced.
     *
     * @param authorizationContext authorizationContext to apply placeholder substitution in.
     * @param headers the headers to apply HeadersPlaceholder substitution with.
     * @return AuthorizationContext as result of placeholder substitution.
     */
    public static AuthorizationContext filterAuthorizationContext(final AuthorizationContext authorizationContext,
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
                .map(id -> apply(id, headers, headersPlaceholder))
                .map(AuthorizationModelFactory::newAuthSubject)
                .collect(Collectors.toList());
        return AuthorizationModelFactory.newAuthContext(subjects);
    }

    /**
     * Apply {@link ThingPlaceholder}s to the passed {@code targets} with the passed {@code thingId}.
     *
     * @param targets {@link Target}s to apply placeholder substitution in.
     * @param thingId the thing ID.
     * @param unresolvedPlaceholderListener callback to call with unresolved placeholders.
     * @return Targets as result of placeholder substitution.
     */
    public static Set<Target> filterTargets(final Set<Target> targets, final String thingId,
            final Consumer<String> unresolvedPlaceholderListener) {
        // check if we have to replace anything at all
        if (targets.stream().map(Target::getAddress).noneMatch(Placeholders::containsAnyPlaceholder)) {
            return targets;
        }

        return targets.stream()
                .map(target -> {
                    final String filtered =
                            applyThingPlaceholder(target.getAddress(), thingId, unresolvedPlaceholderListener);
                    return filtered != null ? target.withAddress(filtered) : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Apply {@link ThingPlaceholder}s to addresses and collect the result as a map.
     *
     * @param addresses addresses to apply placeholder substitution.
     * @param thingId the thing ID.
     * @param unresolvedPlaceholderListener what to do if placeholder substitution fails.
     * @return map from successfully filtered addresses to the result of placeholder substitution.
     */
    public static Map<String, String> filterAddressesAsMap(final Collection<String> addresses, final String thingId,
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
            return apply(address, thingId, PlaceholderFactory.newThingPlaceholder());
        } catch (final UnresolvedPlaceholderException e) {
            unresolvedPlaceholderListener.accept(address);
            return null;
        }
    }

    /**
     * Finds all placeholders ({@code {{ ... }}}) defined in the given {@code template} and tries to replace them
     * by applying the given {@code placeholder}
     *
     * @param template the template string
     * @param value the value containing the source of the replacement that is passed to the placeholder
     * @param thePlaceholder the placeholder to apply to given template
     * @param <T> the input type of the placeholder
     * @return the template string with the resolved values
     * @throws UnresolvedPlaceholderException if {@code verifyAllPlaceholdersResolved} is true and not all
     * placeholders were resolved
     */
    public static <T> String apply(final String template, final T value, final Placeholder<T> thePlaceholder) {
        return apply(template, value, thePlaceholder, false);
    }

    /**
     * Finds all placeholders ({@code {{ ... }}}) defined in the given {@code template} and tries to replace them
     * by applying the given {@code placeholder}
     *
     * @param template the template string
     * @param value the value containing the source of the replacement that is passed to the placeholder
     * @param thePlaceholder the placeholder to apply to given template
     * @param allowUnresolved if {@code false} this method throws an exception if there are any
     * unresolved placeholders after applying the given placeholder
     * @param <T> the input type of the placeholder
     * @return the template string with the resolved values
     * @throws UnresolvedPlaceholderException if {@code allowUnresolved} is true and not all
     * placeholders were resolved
     */
    public static <T> String apply(final String template, final T value, final Placeholder<T> thePlaceholder,
            final boolean allowUnresolved) {
        return doApply(template, thePlaceholder, allowUnresolved,
                placeholder -> thePlaceholder.apply(value, placeholder));
    }

    private static <T> String doApply(final String template,
            final Placeholder<T> thePlaceholder,
            final boolean allowUnresolved,
            final Function<String, Optional<String>> mapper) {

        final Function<String, Optional<String>> placeholderReplacerFunction = placeholder -> {
            final int separatorIndex = placeholder.indexOf(SEPARATOR);
            if (separatorIndex == -1) {
                throw UnresolvedPlaceholderException.newBuilder(placeholder).build();
            }
            final String prefix = placeholder.substring(0, separatorIndex);
            final String placeholderWithoutPrefix = placeholder.substring(separatorIndex + 1);

            return Optional.of(thePlaceholder)
                    .filter(p -> prefix.equals(p.getPrefix()))
                    .filter(p -> p.supports(placeholderWithoutPrefix))
                    .flatMap(p -> mapper.apply(placeholderWithoutPrefix));
        };

        return Placeholders.substitute(template, placeholderReplacerFunction,
                UNRESOLVED_INPUT_HANDLER, allowUnresolved);
    }

    public static <T> String validate(final String template, final Placeholder<T> thePlaceholder,
            final boolean allowUnresolved) {
        return doApply(template, thePlaceholder, allowUnresolved, placeholder -> Optional.of("dummy"));
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
