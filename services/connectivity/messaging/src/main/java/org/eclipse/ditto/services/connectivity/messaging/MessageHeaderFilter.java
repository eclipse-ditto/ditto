/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */

package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;

public class MessageHeaderFilter {

    /**
     * A simple enum describing the filter mode
     */
    public enum Mode {
        EXCLUDE,
        INCLUDE
    }

    private final Mode mode;
    private final Set<String> headerNames;

    /**
     * Creates a new filter
     * @param mode the filter mode
     * @param headerNames the header names
     */
    public MessageHeaderFilter(final Mode mode, final String... headerNames) {
        this(mode, Arrays.asList(headerNames));
    }

    /**
     * Creates a new filter
     * @param mode the filter mode
     * @param headerNames the header names
     */
    public MessageHeaderFilter(final Mode mode, final Collection<String> headerNames) {
        checkNotNull(mode);
        this.mode = mode;
        this.headerNames = new HashSet<>(headerNames);
    }

    /**
     * Apply this filter to a message. This will create a copy of the message with filtered headers.
     * @param message the message
     * @return the filtered messeage
     */
    public ExternalMessage apply(final ExternalMessage message) {
        checkNotNull(mode);
        return ConnectivityModelFactory.newExternalMessageBuilder(message)
                .withHeaders(message.getHeaders().entrySet().stream()
                        .filter(e -> filterPredicate(e.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .build();
    }

    private boolean filterPredicate(final String headerName) {
        switch (mode) {
            case EXCLUDE:
                return !headerNames.contains(headerName);
            case INCLUDE:
                return headerNames.contains(headerName);
            default:
                return true; // don't know what to do, so assume it's ok to keep the header.
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final MessageHeaderFilter that = (MessageHeaderFilter) o;
        return mode == that.mode &&
                Objects.equals(headerNames, that.headerNames);
    }

    @Override
    public int hashCode() {

        return Objects.hash(mode, headerNames);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "mode=" + mode +
                ", headerNames=" + headerNames +
                "]";
    }
}
