/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */

package org.eclipse.ditto.services.connectivity.messaging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingIdInvalidException;

/**
 * A filter implementation to replace defined placeholders with their values.
 */
final class PlaceholderFilter {

    private static final String PLACEHOLDER_START = "{{";
    private static final String PLACEHOLDER_END = "}}";
    private static final String PLACEHOLDER_REGEX =
            Pattern.quote(PLACEHOLDER_START) + " *((?:\\w|[-_])+):((?:\\w|[-_])+) *" + Pattern.quote(PLACEHOLDER_END);
    private static final Pattern PATTERN = Pattern.compile(PLACEHOLDER_REGEX);
    private static final Pattern THING_ID_PATTERN = Pattern.compile(Thing.ID_REGEX);

    AuthorizationContext filterAuthorizationContext(final AuthorizationContext authorizationContext,
            final Map<String, String> headers) {

        // check if we have to replace anything at all
        if (authorizationContext.stream()
                .noneMatch(authorizationSubject -> containsPlaceholder(authorizationSubject.getId()))) {
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
        if (targets.stream().map(Target::getAddress).noneMatch(PlaceholderFilter::containsPlaceholder)) {
            return targets;
        }

        final ThingPlaceholder thingPlaceholder = new ThingPlaceholder(thingId);
        return targets.stream()
                .map(target -> {
                    final String filtered =
                            applyThingPlaceholder(target.getAddress(), thingPlaceholder, unresolvedPlaceholderListener);
                    return filtered != null ? ConnectivityModelFactory.newTarget(target, filtered) : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    Set<String> filterAddresses(final Set<String> addresses, final String thingId,
            final Consumer<String> unresolvedPlaceholderListener) {
        // check if we have to replace anything at all
        if (addresses.stream().noneMatch(PlaceholderFilter::containsPlaceholder)) {
            return addresses;
        }

        final ThingPlaceholder thingPlaceholder = new ThingPlaceholder(thingId);
        return addresses.stream()
                .map(address -> applyThingPlaceholder(address, thingPlaceholder, unresolvedPlaceholderListener))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
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

    String apply(final String source, final Placeholder requiredPlaceHolder,
            final Placeholder... optionalPlaceholders) {
        final List<Placeholder> placeholders = new ArrayList<>(optionalPlaceholders.length + 1);
        placeholders.add(requiredPlaceHolder);
        placeholders.addAll(Arrays.asList(optionalPlaceholders));
        final Matcher matcher = PATTERN.matcher(source);
        final StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            final String prefix = matcher.group(1);
            final String placeholder = matcher.group(2);
            placeholders.stream()
                    .filter(p -> p.getPrefix().equals(prefix))
                    .filter(p -> p.supports(placeholder))
                    .map(p -> p.apply(placeholder))
                    .map(o -> o.orElseThrow(() -> UnresolvedPlaceholderException.newBuilder(matcher.group()).build()))
                    .filter(replacement -> !PATTERN.matcher(replacement).matches())
                    .forEach(replacement -> matcher.appendReplacement(sb, replacement));
        }
        matcher.appendTail(sb);
        return checkAllPlaceholdersResolved(sb.toString());
    }

    private static String checkAllPlaceholdersResolved(final String s) {
        if (containsPlaceholder(s)) {
            throw UnresolvedPlaceholderException.newBuilder().message(s).build();
        }
        return s;
    }

    private static boolean containsPlaceholder(final String value) {
        return value.contains(PLACEHOLDER_START) || value.contains(PLACEHOLDER_END);
    }

    /**
     *
     */
    interface Placeholder {

        String getPrefix();

        boolean supports(String name);

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
