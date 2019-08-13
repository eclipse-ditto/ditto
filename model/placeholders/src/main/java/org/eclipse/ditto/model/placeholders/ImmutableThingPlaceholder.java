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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.ThingId;

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
    public Optional<String> resolve(final CharSequence thingId, final String placeholder) {
        argumentNotEmpty(placeholder, "placeholder");
        checkNotNull(thingId, "Thing ID");
        return doResolve(ThingId.asThingId(thingId), placeholder);
    }

    private Optional<String> doResolve(final ThingId thingId, final String placeholder) {
        switch (placeholder) {
            case NAMESPACE_PLACEHOLDER:
                return Optional.of(thingId.getNameSpace());
            case NAME_PLACEHOLDER:
                return Optional.of(thingId.getName());
            case ID_PLACEHOLDER:
                return Optional.of(thingId.toString());
            default:
                return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[]";
    }
}
