/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.ditto.services.connectivity.messaging;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.common.Placeholders;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingIdInvalidException;

/**
 * A filter implementation to replace defined placeholders with their values.
 */
public final class PlaceholderFilter {

    private static final Pattern THING_ID_PATTERN = Pattern.compile(Thing.ID_REGEX);
    private static final String SEPARATOR = ":";
    private static final Function<String, DittoRuntimeException> UNRESOLVED_INPUT_HANDLER = unresolvedInput ->
            UnresolvedPlaceholderException.newBuilder()
                    .message("At least one placeholder could not be resolved for the input: '" +
                            unresolvedInput + "'")
                    .build();

    AuthorizationContext filterAuthorizationContext(final AuthorizationContext authorizationContext,
            final Map<String, String> headers) {

        // check if we have to replace anything at all
        if (authorizationContext.stream()
                .noneMatch(authorizationSubject -> Placeholders.containsAnyPlaceholder(authorizationSubject.getId()))) {
            return authorizationContext;
        }

        final HeadersPlaceholder headersPlaceholder = new HeadersPlaceholder(headers);
        final List<AuthorizationSubject> subjects = authorizationContext.stream()
                .map(AuthorizationSubject::getId)
                .map(id -> apply(id, headersPlaceholder))
                .map(AuthorizationModelFactory::newAuthSubject)
                .collect(Collectors.toList());
        return AuthorizationModelFactory.newAuthContext(subjects);
    }

    Set<Target> filterTargets(final Set<Target> targets, final String thingId,
            final Consumer<String> unresolvedPlaceholderListener) {
        // check if we have to replace anything at all
        if (targets.stream().map(Target::getAddress).noneMatch(Placeholders::containsAnyPlaceholder)) {
            return targets;
        }

        final ThingPlaceholder thingPlaceholder = new ThingPlaceholder(thingId);
        return targets.stream()
                .map(target -> {
                    final String filtered =
                            applyThingPlaceholder(target.getAddress(), thingPlaceholder, unresolvedPlaceholderListener);
                    return filtered != null ? target.withAddress(filtered) : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    Collection<String> filterAddresses(final Collection<String> addresses, final String thingId,
            final Consumer<String> unresolvedPlaceholderListener) {

        // check if we have to replace anything at all
        if (addresses.stream().noneMatch(Placeholders::containsAnyPlaceholder)) {
            return addresses;
        }

        return filterAddressesAsMap(addresses, thingId, unresolvedPlaceholderListener).values();
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

        final ThingPlaceholder thingPlaceholder = new ThingPlaceholder(thingId);
        return addresses.stream()
                .flatMap(address -> {
                    final String filteredAddress =
                            applyThingPlaceholder(address, thingPlaceholder, unresolvedPlaceholderListener);
                    return filteredAddress == null
                            ? Stream.empty()
                            : Stream.of(new AbstractMap.SimpleEntry<>(address, filteredAddress));
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Nullable
    private String applyThingPlaceholder(final String address, final ThingPlaceholder thingPlaceholder,
            final Consumer<String> unresolvedPlaceholderListener) {
        try {
            return apply(address, thingPlaceholder);
        } catch (final UnresolvedPlaceholderException e) {
            unresolvedPlaceholderListener.accept(address);
            return null;
        }
    }

    String apply(final String input, final Placeholder requiredPlaceHolder,
            final Placeholder... optionalPlaceholders) {
        final List<Placeholder> placeholders = new ArrayList<>(optionalPlaceholders.length + 1);
        placeholders.add(requiredPlaceHolder);
        placeholders.addAll(Arrays.asList(optionalPlaceholders));

        final Function<String, String> placeholderReplacerFunction = placeholder -> {
            final int separatorIndex = placeholder.indexOf(SEPARATOR);
            if (separatorIndex == -1) {
                throw UnresolvedPlaceholderException.newBuilder(placeholder).build();
            }
            final String prefix = placeholder.substring(0, separatorIndex);
            final String placeholderWithoutSuffix = placeholder.substring(separatorIndex + 1);

            return placeholders.stream()
                    .filter(p -> p.getPrefix().equals(prefix) && p.supports(placeholderWithoutSuffix))
                    .findAny()
                    .flatMap(p -> p.apply(placeholderWithoutSuffix))
                    .orElseThrow(() -> UnresolvedPlaceholderException.newBuilder(placeholder).build());
        };

        return Placeholders.substitute(input, placeholderReplacerFunction, UNRESOLVED_INPUT_HANDLER);
    }

    /**
     * Definition of a placeholder variable.
     */
    interface Placeholder {

        /**
         * The part of the placeholder variable before ':'.
         *
         * @return the prefix.
         */
        String getPrefix();

        /**
         * Test whether a placeholder name (i. e., the part after ':') is supported.
         *
         * @param name the placeholder name.
         * @return whether the placeholder name is supported.
         */
        boolean supports(String name);

        /**
         * Evaluate the placeholder variable by name.
         *
         * @param value the placeholder variable name (i. e., the part after ':').
         * @return value of the placeholder variable if the placeholder name is supported, or an empty optional
         * otherwise.
         */
        Optional<String> apply(final String value);
    }

    private static class HeadersPlaceholder implements Placeholder {

        private final Map<String, String> headers;

        private HeadersPlaceholder(final Map<String, String> headers) {
            this.headers = headers;
        }

        @Override
        public String getPrefix() {
            return "header";
        }

        @Override
        public boolean supports(final String name) {
            return true;
        }

        public Optional<String> apply(final String header) {
            ConditionChecker.argumentNotEmpty(header, "header");
            return Optional.ofNullable(headers.get(header));
        }
    }

    private static class ThingPlaceholder implements Placeholder {

        private static final String ID_PLACEHOLDER = "id";
        private static final String NAMESPACE_PLACEHOLDER = "namespace";
        private static final String NAME_PLACEHOLDER = "name";
        private static final List<String> SUPPORTED = Arrays.asList(ID_PLACEHOLDER, NAMESPACE_PLACEHOLDER,
                NAME_PLACEHOLDER);
        private final String namespace;
        private final String name;
        private final String id;

        private ThingPlaceholder(final String thingId) {
            final Matcher matcher = THING_ID_PATTERN.matcher(thingId);
            if (!matcher.matches()) {
                throw ThingIdInvalidException.newBuilder(thingId).build();
            }
            namespace = matcher.group("ns");
            name = matcher.group("id");
            id = thingId;
        }

        @Override
        public String getPrefix() {
            return "thing";
        }

        @Override
        public boolean supports(final String name) {
            return SUPPORTED.contains(name);
        }

        public Optional<String> apply(final String placeholder) {
            ConditionChecker.argumentNotEmpty(placeholder, "placeholder");
            switch (placeholder) {
                case NAMESPACE_PLACEHOLDER:
                    return Optional.of(namespace);
                case NAME_PLACEHOLDER:
                    return Optional.of(name);
                case ID_PLACEHOLDER:
                    return Optional.of(id);
                default:
                    return Optional.empty();
            }
        }
    }

    static Placeholder headers(final Map<String, String> headers) {
        return new HeadersPlaceholder(headers);
    }

    static Placeholder thing(final String id) {
        return new ThingPlaceholder(id);
    }
}
