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
package org.eclipse.ditto.services.connectivity.mapping;

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;

/**
 * A message mapper implementation to convert between raw message payload and external message payload.
 */
@PayloadMapper(alias = {"RawMessage"})
public final class RawMessageMapper extends AbstractMessageMapper {

    // TODO: add options, e.g., default content type for incoming
    private static final Map<String, String> DEFAULT_OPTIONS = Map.of();

    /**
     * The context representing this mapper
     */
    public static final MappingContext CONTEXT = ConnectivityModelFactory.newMappingContext(
            RawMessageMapper.class.getCanonicalName(),
            DEFAULT_OPTIONS
    );

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public Map<String, String> getDefaultOptions() {
        return DEFAULT_OPTIONS;
    }

}
