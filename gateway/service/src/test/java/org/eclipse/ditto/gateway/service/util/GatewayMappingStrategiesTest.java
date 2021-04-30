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
package org.eclipse.ditto.gateway.service.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import org.assertj.core.api.AbstractMapAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.messages.model.AuthorizationSubjectBlockedException;
import org.eclipse.ditto.messages.model.MessageFormatInvalidException;
import org.eclipse.ditto.messages.model.MessageSendNotAllowedException;
import org.eclipse.ditto.messages.model.MessageTimeoutException;
import org.eclipse.ditto.messages.model.SubjectInvalidException;
import org.eclipse.ditto.messages.model.ThingIdInvalidException;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.api.PoliciesMappingStrategies;
import org.eclipse.ditto.things.api.ThingsMappingStrategies;
import org.eclipse.ditto.thingsearch.api.ThingSearchMappingStrategies;
import org.eclipse.ditto.policies.model.signals.announcements.SubjectDeletionAnnouncement;
import org.eclipse.ditto.base.model.signals.JsonParsable;
import org.eclipse.ditto.base.api.devops.signals.commands.ChangeLogLevel;
import org.eclipse.ditto.base.api.devops.signals.commands.ChangeLogLevelResponse;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveLoggerConfig;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveLoggerConfigResponse;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatistics;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatisticsDetails;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatisticsResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessageResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessageResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendMessageAcceptedResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessageResponse;
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
                .knows(ThingIdInvalidException.ERROR_CODE);
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
    public void deserializeSubjectDeletionAnnouncement() {
        final Instant expiry = Instant.now();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final SubjectDeletionAnnouncement announcement = SubjectDeletionAnnouncement.of(
                PolicyId.of("policy:id"),
                expiry,
                Collections.singleton(SubjectId.newInstance("ditto:ditto")),
                dittoHeaders
        );

        final JsonObject json = announcement.toJson();
        final PoliciesMappingStrategies underTest = PoliciesMappingStrategies.getInstance();
        final Jsonifiable<?> output = underTest.getMappingStrategy(announcement.getManifest())
                .orElseThrow()
                .parse(json, dittoHeaders);

        assertThat(output).isEqualTo(announcement);
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
