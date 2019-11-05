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
package org.eclipse.ditto.services.connectivity.mapping;

import java.util.Collection;

/**
 * Abstract implementation of {@link MessageMapper} which adds an id field and also its initialization from mapping
 * configuration (id is not passed as constructor argument because the mappers are created by reflection).
 */
public abstract class AbstractMessageMapper implements MessageMapper {

    private String id;
    private Collection<String> contentTypeBlacklist;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Collection<String> getContentTypeBlacklist() {
        return contentTypeBlacklist;
    }

    @Override
    public final void configure(final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        this.id = configuration.getId();
        this.contentTypeBlacklist = configuration.getContentTypeBlacklist();
        doConfigure(mappingConfig, configuration);
    }

    /**
     * Applies the mapper specific configuration.
     *
     * @param mappingConfig the service configuration for the mapping.
     * @param configuration the mapper specific configuration configured in scope of a single connection.
     */
    protected void doConfigure(final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        // noop default
    }
}
