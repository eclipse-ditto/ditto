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
package org.eclipse.ditto.services.connectivity.mapping.test;

import static java.util.Collections.emptyList;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperConfiguration;
import org.eclipse.ditto.services.connectivity.mapping.PayloadMapper;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;

@PayloadMapper(alias = "test")
public final class MockMapper implements MessageMapper {

    public static final String OPT_IS_VALID = "Mock";

    MockMapper() {
        super();
    }

    @Override
    public String getId() {
        return "mock";
    }

    @Override
    public Collection<String> getContentTypeBlacklist() {
        return Collections.emptyList();
    }

    @Override
    public void configure(@Nonnull final MappingConfig mappingConfig,
            @Nonnull final MessageMapperConfiguration configuration) {

        configuration.findProperty(OPT_IS_VALID).map(Boolean::valueOf).filter(Boolean.TRUE::equals).orElseThrow
                (() -> MessageMapperConfigurationInvalidException.newBuilder(OPT_IS_VALID).build());
    }

    @Override
    @Nonnull
    public List<Adaptable> map(@Nonnull final ExternalMessage message) {
        return emptyList();
    }

    @Override
    @Nonnull
    public List<ExternalMessage> map(@Nonnull final Adaptable adaptable) {
        return emptyList();
    }

}
