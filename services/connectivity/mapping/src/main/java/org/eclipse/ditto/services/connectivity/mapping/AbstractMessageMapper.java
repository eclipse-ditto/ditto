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

/**
 * TODO
 */
public abstract class AbstractMessageMapper implements MessageMapper {

    protected String id;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public final void configure(final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        this.id = configuration.getId();
        doConfigure(mappingConfig, configuration);
    }

    protected void doConfigure(final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        // noop default
    }
}
