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
package org.eclipse.ditto.protocol.placeholders;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.signals.WithResource;

/**
 * Placeholder implementation that replaces:
 * <ul>
 * <li>{@code resource:type} -> {@code "thing"|"policy"|"message"|"connection"}</li>
 * <li>{@code resource:path} to the path of the resource included in a Signal</li>
 * </ul>
 * The input value is a WithResource.
 */
@Immutable
final class ImmutableResourcePlaceholder implements ResourcePlaceholder {

    /**
     * Singleton instance of the ImmutableResourcePlaceholder.
     */
    static final ImmutableResourcePlaceholder INSTANCE = new ImmutableResourcePlaceholder();

    private static final String TYPE_PLACEHOLDER = "type";
    private static final String PATH_PLACEHOLDER = "path";

    private static final List<String> SUPPORTED = Collections.unmodifiableList(
            Arrays.asList(TYPE_PLACEHOLDER, PATH_PLACEHOLDER));

    private ImmutableResourcePlaceholder() {
    }

    @Override
    public String getPrefix() {
        return "resource";
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
    public List<String> resolveValues(final WithResource withResource, final String placeholder) {
        ConditionChecker.argumentNotEmpty(placeholder, "placeholder");
        switch (placeholder) {
            case TYPE_PLACEHOLDER:
                return Collections.singletonList(withResource.getResourceType());
            case PATH_PLACEHOLDER:
                return Collections.singletonList(withResource.getResourcePath().toString());
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[]";
    }
}
