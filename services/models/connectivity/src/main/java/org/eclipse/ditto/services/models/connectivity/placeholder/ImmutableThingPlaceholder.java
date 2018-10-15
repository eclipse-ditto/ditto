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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingIdInvalidException;

/**
 * Placeholder implementation that replaces {@code thing:id}, {@code thing:namespace} and {@code thing:name}. The
 * input value is a String and must be a valid Thing ID.
 */
@Immutable
final class ImmutableThingPlaceholder implements ThingPlaceholder {

    /**
     * Singleton instance of the ImmutableThingPlaceholder.
     */
    static final ImmutableThingPlaceholder INSTANCE = new ImmutableThingPlaceholder();

    private static final String ID_PLACEHOLDER = "id";
    private static final String NAMESPACE_PLACEHOLDER = "namespace";
    private static final String NAME_PLACEHOLDER = "name";

    private static final List<String> SUPPORTED = Collections.unmodifiableList(
            Arrays.asList(ID_PLACEHOLDER, NAMESPACE_PLACEHOLDER, NAME_PLACEHOLDER));

    private static final Pattern THING_ID_PATTERN = Pattern.compile(Thing.ID_REGEX);

    private ImmutableThingPlaceholder() {
    }

    @Override
    public String getPrefix() {
        return "thing";
    }

    @Override
    public List<String> getSupportedNames() {
        return SUPPORTED;
    }

    @Override
    public boolean supports(final String name) {
        return SUPPORTED.contains(name);
    }

    @Override
    public Optional<String> apply(final String thingId, final String placeholder) {
        ConditionChecker.argumentNotEmpty(placeholder, "placeholder");
        switch (placeholder) {
            case NAMESPACE_PLACEHOLDER:
                return Optional.of(extractNamespace(thingId));
            case NAME_PLACEHOLDER:
                return Optional.of(extractName(thingId));
            case ID_PLACEHOLDER:
                return Optional.of(extractThingId(thingId));
            default:
                return Optional.empty();
        }
    }

    private String extractNamespace(final String thingId) {
        return getMatcher(thingId).group("ns");
    }

    private String extractName(final String thingId) {
        return getMatcher(thingId).group("id");
    }

    private String extractThingId(final String thingId) {
        final Matcher matcher = getMatcher(thingId);
        return matcher.group("ns") + ":" + matcher.group("id");
    }

    private Matcher getMatcher(final String thingId) {
        final Matcher matcher = THING_ID_PATTERN.matcher(thingId);
        if (!matcher.matches()) {
            throw ThingIdInvalidException.newBuilder(thingId).build();
        }
        return matcher;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[]";
    }
}
