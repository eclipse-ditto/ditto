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
package org.eclipse.ditto.services.gateway.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import org.assertj.core.api.AbstractMapAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.messages.AuthorizationSubjectBlockedException;
import org.eclipse.ditto.model.messages.MessageFormatInvalidException;
import org.eclipse.ditto.model.messages.MessageSendNotAllowedException;
import org.eclipse.ditto.model.messages.MessageTimeoutException;
import org.eclipse.ditto.model.messages.SubjectInvalidException;
import org.eclipse.ditto.model.messages.TimeoutInvalidException;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.services.models.policies.PoliciesMappingStrategies;
import org.eclipse.ditto.services.models.things.ThingsMappingStrategies;
import org.eclipse.ditto.services.models.thingsearch.ThingSearchMappingStrategies;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.devops.ChangeLogLevel;
import org.eclipse.ditto.signals.commands.devops.ChangeLogLevelResponse;
import org.eclipse.ditto.signals.commands.devops.RetrieveLoggerConfig;
import org.eclipse.ditto.signals.commands.devops.RetrieveLoggerConfigResponse;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatistics;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsDetails;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsResponse;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessageResponse;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessage;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessageResponse;
import org.eclipse.ditto.signals.commands.messages.SendMessageAcceptedResponse;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;
import org.eclipse.ditto.signals.commands.messages.SendThingMessageResponse;
import org.eclipse.ditto.signals.notifications.policies.SubjectExpiryNotification;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link GatewayMappingStrategies}.
 */
public final class GatewayMappingStrategiesTest {

    private GatewayMappingStrategies underTest;

    @Before
    public void setUp() {
        underTest = GatewayMappingStrategies.getInstance();
    }

    @Test
    public void allThingSearchStrategiesAreKnown() {
        assertThatStrategy().knowsAllOf(ThingSearchMappingStrategies.getInstance());
    }

    @Test
    public void allPoliciesMappingStrategiesAreKnown() {
        assertThatStrategy().knowsAllOf(PoliciesMappingStrategies.getInstance());
    }

    @Test
    public void allThingsMappingStrategiesAreKnown() {
        assertThatStrategy().knowsAllOf(ThingsMappingStrategies.getInstance());
    }

    @Test
    public void messagesStrategiesAreKnown() {
        assertThatStrategy()
                .knows(SendClaimMessage.TYPE)
                .knows(SendThingMessage.TYPE)
                .knows(SendFeatureMessage.TYPE)
                .knows(SendClaimMessageResponse.TYPE)
                .knows(SendMessageAcceptedResponse.TYPE)
                .knows(SendThingMessageResponse.TYPE)
                .knows(SendFeatureMessageResponse.TYPE)
                .knows(AuthorizationSubjectBlockedException.ERROR_CODE)
                .knows(MessageFormatInvalidException.ERROR_CODE)
                .knows(MessageSendNotAllowedException.ERROR_CODE)
                .knows(MessageTimeoutException.ERROR_CODE)
                .knows(SubjectInvalidException.ERROR_CODE)
                .knows(TimeoutInvalidException.ERROR_CODE);
    }

    @Test
    public void devOpsStrategiesAreKnown() {
        assertThatStrategy()
                .knows(ChangeLogLevel.TYPE)
                .knows(RetrieveLoggerConfig.TYPE)
                .knows(RetrieveStatistics.TYPE)
                .knows(RetrieveStatisticsDetails.TYPE)
                .knows(ChangeLogLevelResponse.TYPE)
                .knows(RetrieveLoggerConfigResponse.TYPE)
                .knows(RetrieveStatisticsResponse.TYPE);
    }

    @Test
    public void deserializeSubjectExpiryNotification() {
        final Instant expiry = Instant.now();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final SubjectExpiryNotification notification = SubjectExpiryNotification.of(
                PolicyId.of("policy:id"),
                expiry,
                Collections.singleton(SubjectId.newInstance("ditto:ditto")),
                dittoHeaders
        );

        final JsonObject json = notification.toJson();
        final PoliciesMappingStrategies underTest = PoliciesMappingStrategies.getInstance();
        final Jsonifiable<?> output = underTest.getMappingStrategy(notification.getManifest())
                .orElseThrow()
                .parse(json, dittoHeaders);

        assertThat(output).isEqualTo(notification);
    }

    private StrategyAssert assertThatStrategy() {
        return new StrategyAssert(underTest);
    }

    private static final class StrategyAssert
            extends
            AbstractMapAssert<StrategyAssert, Map<String, JsonParsable<Jsonifiable<?>>>, String, JsonParsable<Jsonifiable<?>>> {

        StrategyAssert(final Map<String, JsonParsable<Jsonifiable<?>>> strategies) {
            super(strategies, StrategyAssert.class);
        }

        StrategyAssert knows(final String key) {
            Assertions.assertThat(actual)
                    .as("Strategy for <%s> is available", key)
                    .overridingErrorMessage("Strategy did not contain a mapping function for <%s>", key)
                    .containsKey(key);
            return myself;
        }

        StrategyAssert knowsAllOf(final Map<String, JsonParsable<Jsonifiable<?>>> mappingStrategies) {
            mappingStrategies.keySet().forEach(this::knows);
            return myself;
        }

    }

}
