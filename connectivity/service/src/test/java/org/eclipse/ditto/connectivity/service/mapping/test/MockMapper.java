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
package org.eclipse.ditto.connectivity.service.mapping.test;

import static java.util.Collections.emptyList;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperConfiguration;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

@AllParametersAndReturnValuesAreNonnullByDefault
public final class MockMapper implements MessageMapper {

    public static final String ALIAS = "MockMapper";

    public static final String OPT_IS_VALID = "Mock";

    public MockMapper(final ActorSystem actorSystem, final Config config) {

    }

    @Override
    public String getId() {
        return "mock";
    }

    @Override
    public String getAlias() {
        return ALIAS;
    }

    @Override
    public boolean isConfigurationMandatory() {
        return false;
    }

    @Override
    public MessageMapper createNewMapperInstance() {
        return this;
    }

    @Override
    public Collection<String> getContentTypeBlocklist() {
        return Collections.emptyList();
    }

    @Override
    public void configure(final Connection connection,
            final ConnectivityConfig connectivityConfig,
            final MessageMapperConfiguration configuration,
            final ActorSystem actorSystem) {

        configuration.findProperty(OPT_IS_VALID).map(Boolean::valueOf).filter(Boolean.TRUE::equals).orElseThrow
                (() -> MessageMapperConfigurationInvalidException.newBuilder(OPT_IS_VALID).build());
    }

    @Override
    @Nonnull
    public List<Adaptable> map(final ExternalMessage message) {
        return emptyList();
    }

    @Override
    public DittoHeaders getAdditionalInboundHeaders(final ExternalMessage message) {
        return DittoHeaders.empty();
    }
    @Override
    @Nonnull
    public List<ExternalMessage> map(final Adaptable adaptable) {
        return emptyList();
    }

    @Override
    public Map<String, String> getIncomingConditions() {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, String> getOutgoingConditions() {
        return Collections.emptyMap();
    }

}
