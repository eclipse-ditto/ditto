/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers.translator;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.HeaderDefinition;

/**
 * This {@link HeaderEntryFilter} checks if the header definition for the given key allows or forbids the value to be
 * written to or read from external headers.
 */
@Immutable
final class CheckExternalFilter extends AbstractHeaderEntryFilter {

    private final Map<String, HeaderDefinition> headerDefinitions;
    private final Predicate<HeaderDefinition> headerDefinitionPredicate;

    private CheckExternalFilter(final Map<String, HeaderDefinition> headerDefinitions,
            final Predicate<HeaderDefinition> headerDefinitionPredicate) {

        this.headerDefinitions = Collections.unmodifiableMap(checkNotNull(headerDefinitions, "headerDefinitions"));
        this.headerDefinitionPredicate = headerDefinitionPredicate;
    }

    /**
     * Returns an instance of {@code CheckExternalFilter} which checks if the header definition for the given key allows
     * or forbids the value to be read from external headers.
     * If the value should be read from external headers or if there is no header definition for the given key, the
     * value is returned as it is.
     * If the value is explicitly exempted to be read from external headers, {@code null} is returned.
     *
     * @param headerDefinitions the header definitions for determining whether a header entry may be read from external
     * headers or not.
     * @return the instance.
     * @throws NullPointerException if {@code headerDefinitions} is {@code null}.
     */
    public static CheckExternalFilter shouldReadFromExternal(final Map<String, HeaderDefinition> headerDefinitions) {
        return new CheckExternalFilter(headerDefinitions,
                isNull().or(HeaderDefinition::shouldReadFromExternalHeaders));
    }

    /**
     * Returns an instance of {@code CheckExternalFilter} which checks if the header definition for the given key allows
     * or forbids the value to be written to external headers.
     * If the value should be written to external headers or if there is no header definition for the given key, the
     * value is returned as it is.
     * If the value is explicitly exempted to be written to external headers, {@code null} is returned.
     *
     * @param headerDefinitions the header definitions for determining whether a header entry may be written to external
     * headers or not.
     * @return the instance.
     * @throws NullPointerException if {@code headerDefinitions} is {@code null}.
     */
    public static CheckExternalFilter shouldWriteToExternal(final Map<String, HeaderDefinition> headerDefinitions) {
        return new CheckExternalFilter(headerDefinitions,
                isNull().or(HeaderDefinition::shouldWriteToExternalHeaders));
    }

    /**
     * Returns an instance of {@code CheckExternalFilter} which checks if a header definition for the given key exists.
     *
     * @param headerDefinitions the header definitions for determining whether a header entry is defined by a
     * HeaderDefinition or not.
     * @return the instance.
     * @throws NullPointerException if {@code headerDefinitions} is {@code null}.
     * @since 1.1.0
     */
    public static CheckExternalFilter existsAsHeaderDefinition(final Map<String, HeaderDefinition> headerDefinitions) {
        return new CheckExternalFilter(headerDefinitions, Objects::nonNull);
    }

    private static Predicate<HeaderDefinition> isNull() {
        return Objects::isNull;
    }

    @Nullable
    @Override
    protected String filterValue(final String key, final String value) {
        @Nullable final HeaderDefinition headerDefinition = headerDefinitions.get(key.toLowerCase());
        if (headerDefinitionPredicate.test(headerDefinition)) {
            return value;
        }
        return null;
    }

}
