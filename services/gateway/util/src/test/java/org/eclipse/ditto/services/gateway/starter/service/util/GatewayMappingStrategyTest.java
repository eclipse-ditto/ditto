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
package org.eclipse.ditto.services.gateway.starter.service.util;

import java.util.Map;
import java.util.function.BiFunction;

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
import org.eclipse.ditto.services.models.policies.PoliciesMappingStrategy;
import org.eclipse.ditto.services.models.things.ThingsMappingStrategy;
import org.eclipse.ditto.services.models.thingsearch.ThingSearchMappingStrategy;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;
import org.eclipse.ditto.services.utils.distributedcache.model.BaseCacheEntry;
import org.eclipse.ditto.signals.commands.devops.ChangeLogLevel;
import org.eclipse.ditto.signals.commands.devops.ChangeLogLevelResponse;
import org.eclipse.ditto.signals.commands.devops.RetrieveLoggerConfig;
import org.eclipse.ditto.signals.commands.devops.RetrieveLoggerConfigResponse;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatistics;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsResponse;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessageResponse;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessage;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessageResponse;
import org.eclipse.ditto.signals.commands.messages.SendMessageAcceptedResponse;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;
import org.eclipse.ditto.signals.commands.messages.SendThingMessageResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.services.gateway.starter.service.util.GatewayMappingStrategyTest}.
 */
public final class GatewayMappingStrategyTest {

    private Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> strategy;

    @Before
    public void setUp() {
        final MappingStrategy underTest = new GatewayMappingStrategy();
        strategy = underTest.determineStrategy();
    }

    @Test
    public void allThingSearchStrategiesAreKnown() {
        assertThatStrategy().knowsAllOf(new ThingSearchMappingStrategy());
    }

    @Test
    public void allPoliciesMappingStrategiesAreKnown() {
        assertThatStrategy().knowsAllOf(new PoliciesMappingStrategy());
    }

    @Test
    public void allThingsMappingStrategiesAreKnown() {
        assertThatStrategy().knowsAllOf(new ThingsMappingStrategy());
    }

    @Test
    public void baseCacheEntryIsKnown() {
        assertThatStrategy().knows(BaseCacheEntry.class.getSimpleName());
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
                .knows(ChangeLogLevelResponse.TYPE)
                .knows(RetrieveLoggerConfigResponse.TYPE)
                .knows(RetrieveStatisticsResponse.TYPE);
    }

    private StrategyAssert assertThatStrategy() {
        return new StrategyAssert(strategy);
    }

    private static final class StrategyAssert extends AbstractMapAssert<StrategyAssert,
            Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>>,
            String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> {

        public StrategyAssert(final Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> strategy) {
            super(strategy, StrategyAssert.class);
        }

        public StrategyAssert knows(final String key) {
            Assertions.assertThat(actual)
                    .as("Strategy for <%s> is available", key)
                    .overridingErrorMessage("Strategy did not contain a mapping function for <%s>", key)
                    .containsKey(key);
            return myself;
        }

        public StrategyAssert knowsAllOf(final MappingStrategy mappingStrategy) {
            final Map<String, ?> determinedStrategy = mappingStrategy.determineStrategy();

            determinedStrategy.keySet().forEach(this::knows);
            return myself;
        }

    }

}