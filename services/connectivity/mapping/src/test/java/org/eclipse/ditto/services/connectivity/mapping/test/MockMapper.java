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

import java.util.Optional;

import javax.annotation.Nonnull;

import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperConfiguration;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;

import com.typesafe.config.Config;


public class MockMapper implements MessageMapper {

    public static final String OPT_IS_VALID = "Mock";

    public MockMapper() {
    }

    @Override
    public void configure(@Nonnull final Config mappingConfig, @Nonnull final MessageMapperConfiguration configuration) {
        configuration.findProperty(OPT_IS_VALID).map(Boolean::valueOf).filter(Boolean.TRUE::equals).orElseThrow
                (() -> MessageMapperConfigurationInvalidException.newBuilder(OPT_IS_VALID).build());
    }

    @Override
    @Nonnull
    public Optional<Adaptable> map(@Nonnull final ExternalMessage message) {
        return Optional.empty();
    }

    @Override
    @Nonnull
    public Optional<ExternalMessage> map(@Nonnull final Adaptable adaptable) {
        return Optional.empty();
    }


}
