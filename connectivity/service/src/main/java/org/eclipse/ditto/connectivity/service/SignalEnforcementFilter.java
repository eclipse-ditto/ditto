/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.placeholders.Placeholder;
import org.eclipse.ditto.placeholders.PlaceholderFilter;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;

/**
 * Implementation of an {@link EnforcementFilter} which is applicable to signals.
 *
 * This class can enforce that a Signal fulfils some needs or some requirements. These requirements are defined
 * beforehand at creation time in form of placeholders and an input value. Typically they are specific to a connection
 * type (mqtt, amqp, ...) and it is done where messages are received. That's why this step is separated from the actual
 * match, which is usually done later, after mapping the message to ditto message, in the processing chain of an
 * incoming message.
 */
@Immutable
final class SignalEnforcementFilter implements EnforcementFilter<Signal<?>> {

    private final Enforcement enforcement;
    private final List<Placeholder<EntityId>> filterPlaceholders;
    private final String inputValue;

    SignalEnforcementFilter(final Enforcement enforcement,
            final List<Placeholder<EntityId>> filterPlaceholders,
            final String inputValue) {
        this.enforcement = enforcement;
        this.filterPlaceholders = Collections.unmodifiableList(new ArrayList<>(filterPlaceholders));
        this.inputValue = inputValue;
    }

    /**
     * Matches the input (which must already be known to the {@link EnforcementFilter}) against the values that are
     * resolved from the filterInput by applying the configured placeholder to it. A match in this context is
     * successful if the
     * resolved input is equal to one of the resolved filters.
     *
     * @param filterInput the source from which the the placeholders in the filters are resolved
     * @throws ConnectionSignalIdEnforcementFailedException if the given filter input could be resolved by any
     * placeholder, but did not match the {@link #inputValue}.
     * @throws UnresolvedPlaceholderException if no placeholder could resolve the given filter input.
     */
    @Override
    public void match(final Signal<?> filterInput) {
        final EntityId entityId = extractEntityId(filterInput)
                .orElseThrow(() -> getEnforcementFailedException(filterInput.getDittoHeaders()));
        final List<UnresolvedPlaceholderException> exceptions = new LinkedList<>();

        for (final Placeholder<EntityId> filterPlaceholder : filterPlaceholders) {
            for (final String filter : enforcement.getFilters()) {
                try {
                    final boolean anyResolvedToInputValue = PlaceholderFilter.applyForAll(filter, entityId, filterPlaceholder)
                            .stream()
                            .anyMatch(inputValue::equals);
                    if (anyResolvedToInputValue) {
                        return;
                    }
                } catch (final UnresolvedPlaceholderException unresolved) {
                    exceptions.add(unresolved);
                }
            }
        }

        /*
            exceptions.size() == filterPlaceholders.size():
              the configured filter could not be resolved by any of the placeholders
                -> re-throw UnresolvedPlaceholderException
        */
        if (exceptions.size() == filterPlaceholders.size()) {
            throw exceptions.get(0);
        }

        /*
            exceptions.size() < filterPlaceholders.size()
                  at least one of the placeholder could resolve the filter but it did not match
                    -> throw ConnectionSignalIdEnforcementFailedException
         */
        throw getEnforcementFailedException(filterInput.getDittoHeaders());
    }

    private ConnectionSignalIdEnforcementFailedException getEnforcementFailedException(
            final DittoHeaders dittoHeaders) {
        return ConnectionSignalIdEnforcementFailedException.newBuilder(inputValue)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private static Optional<EntityId> extractEntityId(final Signal<?> signal) {
        return signal instanceof WithEntityId withEntityId
                ? Optional.of(withEntityId.getEntityId())
                : Optional.empty();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SignalEnforcementFilter that = (SignalEnforcementFilter) o;
        return Objects.equals(enforcement, that.enforcement) &&
                Objects.equals(filterPlaceholders, that.filterPlaceholders) &&
                Objects.equals(inputValue, that.inputValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enforcement, filterPlaceholders, inputValue);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enforcement=" + enforcement +
                ", filterPlaceholders=" + filterPlaceholders +
                ", inputValue=" + inputValue +
                "]";
    }

}
