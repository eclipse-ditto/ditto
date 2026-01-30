/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.edge.service.placeholders;

import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.placeholders.Placeholder;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.things.model.Thing;

/**
 * Placeholder implementation that replaces {@code thing-json:attributes/some-attr} and other arbitrary json values inside
 * a Thing. Delegates to the thing-json placeholder from the placeholders module (working on JsonObject).
 */
@Immutable
final class ImmutableThingJsonPlaceholder implements ThingJsonPlaceholder {

    /**
     * Singleton instance of the ImmutableThingJsonPlaceholder.
     */
    static final ImmutableThingJsonPlaceholder INSTANCE = new ImmutableThingJsonPlaceholder();

    private static final Placeholder<JsonObject> DELEGATE = PlaceholderFactory.newThingJsonPlaceholder();

    @Override
    public String getPrefix() {
        return DELEGATE.getPrefix();
    }

    @Override
    public List<String> getSupportedNames() {
        return DELEGATE.getSupportedNames();
    }

    @Override
    public boolean supports(final String name) {
        return DELEGATE.supports(name);
    }

    @Override
    public List<String> resolveValues(final Thing thing, final String placeholder) {
        return DELEGATE.resolveValues(thing.toJson(FieldType.all()), placeholder);
    }
}
