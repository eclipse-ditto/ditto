/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter;

import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.HeaderDefinition;

/**
 * This factory provides various {@link HeaderEntryFilter}s both atomic ones and composed ones.
 */
@Immutable
final class HeaderEntryFilters {

    private HeaderEntryFilters() {
        throw new AssertionError();
    }

    static HeaderEntryFilter toExternalHeadersFilter(final Map<String, HeaderDefinition> headerDefinitionMap) {
        return shouldWriteToExternal(headerDefinitionMap).andThen(discardDittoAckRequests());
    }

    private static HeaderEntryFilter shouldWriteToExternal(final Map<String, HeaderDefinition> headerDefinitions) {
        return CheckExternalFilter.shouldWriteToExternal(headerDefinitions);
    }

    private static HeaderEntryFilter discardDittoAckRequests() {
        return DittoAckRequestsFilter.getInstance();
    }

    static HeaderEntryFilter fromExternalHeadersFilter(final Map<String, HeaderDefinition> headerDefinitionMap) {
        return CheckExternalFilter.shouldReadFromExternal(headerDefinitionMap);
    }

}
