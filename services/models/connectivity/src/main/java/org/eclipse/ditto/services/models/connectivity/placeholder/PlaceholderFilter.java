/*
 *  Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 *  SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.ditto.services.models.connectivity.placeholder;

import static java.util.regex.Pattern.quote;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;

/**
 * A filter implementation to replace defined placeholders with their values.
 */
public final class PlaceholderFilter {

    private static final String PLACEHOLDER_START = "{{";
    private static final String PLACEHOLDER_END = "}}";
    private static final String CHARS = "(?:\\w|[-])+"; // \w = [a-zA-Z_0-9]
    private static final String SPACES = " *";
    private static final String PLACEHOLDER_REGEX =
            quote(PLACEHOLDER_START) // opening double curly braces
                    + SPACES // arbitrary number of spaces (useful for readability if embedded somewhere)
                    + "(" + CHARS + ")" // the prefix of the placeholder
                    + SEPARATOR // the separator between prefix and value
                    + "(" + CHARS + ")" // the actual value of the placeholder
                    + SPACES // arbitrary number of spaces (useful for readability if embedded somewhere)
                    + quote(PLACEHOLDER_END); // closing double curly braces
    private static final Pattern PATTERN = Pattern.compile(PLACEHOLDER_REGEX);

    public static AuthorizationContext filterAuthorizationContext(final AuthorizationContext authorizationContext,
            final Map<String, String> headers) {

        // check if we have to replace anything at all
        if (authorizationContext.stream()
                .noneMatch(authorizationSubject -> containsPlaceholder(authorizationSubject.getId()))) {
            return authorizationContext;
        }

        final ImmutableHeadersPlaceholder headersPlaceholder = ImmutableHeadersPlaceholder.INSTANCE;
        final List<AuthorizationSubject> subjects = authorizationContext.stream()
                .map(AuthorizationSubject::getId)
                .map(id -> apply(id, headers, headersPlaceholder))
                .map(AuthorizationModelFactory::newAuthSubject)
                .collect(Collectors.toList());
        return AuthorizationModelFactory.newAuthContext(subjects);
    }

    public static Set<Target> filterTargets(final Set<Target> targets, final String thingId,
            final Consumer<String> unresolvedPlaceholderListener) {
        // check if we have to replace anything at all
        if (targets.stream().map(Target::getAddress).noneMatch(PlaceholderFilter::containsPlaceholder)) {
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
     * Apply thing placeholders to addresses and collect the result as a map.
     *
     * @param addresses addresses to apply placeholder substitution.
     * @param thingId the thing ID.
     * @param unresolvedPlaceholderListener what to do if placeholder substitution fails.
     * @return map from successfully filtered addresses to the result of placeholder substitution.
     */
    public Map<String, String> filterAddressesAsMap(final Collection<String> addresses, final String thingId,
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
            return apply(address, thingId, ImmutableThingPlaceholder.INSTANCE);
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
        return apply(template, value, thePlaceholder, true);
    }

    /**
     * Finds all placeholders ({@code {{ ... }}}) defined in the given {@code template} and tries to replace them
     * by applying the given {@code placeholder}
     *
     * @param template the template string
     * @param value the value containing the source of the replacement that is passed to the placeholder
     * @param thePlaceholder the placeholder to apply to given template
     * @param verifyAllPlaceholdersResolved if {@code true} this method throws an exception if there are any
     * unresolved placeholders after apply the given placeholder
     * @param <T> the input type of the placeholder
     * @return the template string with the resolved values
     * @throws UnresolvedPlaceholderException if {@code verifyAllPlaceholdersResolved} is true and not all
     * placeholders were resolved
     */
    public static <T> String apply(final String template, final T value, final Placeholder<T> thePlaceholder,
            final boolean verifyAllPlaceholdersResolved) {
        return doApply(template, thePlaceholder, verifyAllPlaceholdersResolved,
                placeholder -> thePlaceholder.apply(value, placeholder));
    }

    private static <T> String doApply(final String template,
            final Placeholder<T> thePlaceholder,
            final boolean verifyAllPlaceholdersResolved,
            final Function<String, Optional<String>> mapper) {
        final Matcher matcher = PATTERN.matcher(template);
        final StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            final String prefix = matcher.group(1);
            final String placeholder = matcher.group(2);
            Stream.of(thePlaceholder)
                    .filter(p -> p.getPrefix().equals(prefix))
                    .filter(p -> p.supports(placeholder))
                    .map(p -> mapper.apply(placeholder))
                    .map(o -> o.orElseThrow(() -> UnresolvedPlaceholderException.newBuilder(matcher.group()).build()))
                    .filter(replacement -> !PATTERN.matcher(replacement).matches())
                    .forEach(replacement -> matcher.appendReplacement(sb, replacement));
        }
        matcher.appendTail(sb);

        if (verifyAllPlaceholdersResolved) {
            return checkAllPlaceholdersResolved(sb.toString());
        } else {
            return sb.toString();
        }
    }

    public static <T> String validate(final String template, final Placeholder<T> thePlaceholder) {
        return doApply(template, thePlaceholder, true, placeholder -> Optional.of("dummy"));
    }

    static String checkAllPlaceholdersResolved(final String s) {
        if (containsPlaceholder(s)) {
            throw UnresolvedPlaceholderException.newBuilder().message(s).build();
        }
        return s;
    }

    private static boolean containsPlaceholder(final String value) {
        return value.contains(PLACEHOLDER_START) || value.contains(PLACEHOLDER_END);
    }

    private PlaceholderFilter() {
        throw new AssertionError();
    }

}
