/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.cluster.ddata.Key;
import org.apache.pekko.cluster.ddata.ORSet;
import org.apache.pekko.cluster.ddata.ORSetKey;
import org.apache.pekko.cluster.ddata.Replicator;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.commands.DefaultContext;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.devops.FeatureValidationConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationEnforceConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationForbidConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationEnforceConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationForbidConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.eclipse.ditto.things.model.devops.commands.ModifyWotValidationConfig;
import org.eclipse.ditto.things.model.devops.commands.ModifyWotValidationConfigResponse;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigCreated;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigModified;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit test for {@link ModifyWotValidationConfigStrategy}.
 */
public final class ModifyWotValidationConfigStrategyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModifyWotValidationConfigStrategyTest.class);
    private ModifyWotValidationConfigStrategy underTest;
    private WotValidationConfigDData ddata;
    private ActorSystem actorSystem;
    private CommandStrategy.Context<WotValidationConfigId> context;

    @Mock
    private DittoDiagnosticLoggingAdapter logger;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        actorSystem = mock(ActorSystem.class);
        ddata = mock(WotValidationConfigDData.class);
        
        // Set up logger mock
        when(logger.withCorrelationId(any(WithDittoHeaders.class))).thenReturn(logger);
        when(logger.withCorrelationId(any(DittoHeaders.class))).thenReturn(logger);
        when(logger.withCorrelationId(any(CharSequence.class))).thenReturn(logger);
        when(logger.isDebugEnabled()).thenReturn(true);
        
        // Create context using DefaultContext
        final WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        context = DefaultContext.getInstance(configId, logger, actorSystem);
        
        // Mock ddata to return completed future for add()
        when(ddata.add(any(JsonObject.class))).thenReturn(CompletableFuture.completedFuture(null));
        
        underTest = new ModifyWotValidationConfigStrategy(ddata);
    }


    @Test
    public void modifyExistingConfig() {
        // Given
        final WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        final DittoHeaders headers = DittoHeaders.empty();
        final WotValidationConfig existingConfig = WotValidationConfig.of(
                configId,
                true,
                false,
                ThingValidationConfig.of(
                    ThingValidationEnforceConfig.of(true, true, true, true, true),
                    ThingValidationForbidConfig.of(false, true, false, true)
                ),
                FeatureValidationConfig.of(
                    FeatureValidationEnforceConfig.of(true, false, true, false, true, false, true),
                    FeatureValidationForbidConfig.of(false, true, false, true, false, true)
                ),
                Collections.emptyList(),
                WotValidationConfigRevision.of(1L),
                Instant.now(),
                Instant.now(),
                false,
                null
        );
        final WotValidationConfig newConfig = WotValidationConfig.of(
                configId,
                false, // Changed from true to false
                true,  // Changed from false to true
                existingConfig.getThingConfig().orElse(null),
                existingConfig.getFeatureConfig().orElse(null),
                existingConfig.getDynamicConfigs(),
                WotValidationConfigRevision.of(2L),
                existingConfig.getCreated().orElse(null),
                Instant.now(),
                false,
                null
        );
        final ModifyWotValidationConfig command = ModifyWotValidationConfig.of(configId, newConfig, headers);

        // When
        final Result<WotValidationConfigEvent<?>> result = underTest.apply(context, existingConfig, 2L, command);

        // Then
        final ArgumentCaptor<WotValidationConfigEvent<?>> eventCaptor = ArgumentCaptor.forClass(WotValidationConfigEvent.class);
        final ArgumentCaptor<ModifyWotValidationConfigResponse> responseCaptor = ArgumentCaptor.forClass(ModifyWotValidationConfigResponse.class);
        final ResultVisitor<WotValidationConfigEvent<?>> visitor = mock(ResultVisitor.class);

        result.accept(visitor, null);

        verify(visitor).onMutation(eq(command), eventCaptor.capture(), responseCaptor.capture(), eq(false), eq(false), eq(null));

        final WotValidationConfigEvent<?> event = eventCaptor.getValue();
        assertThat(event).isInstanceOf(WotValidationConfigModified.class);
        final WotValidationConfigModified modifiedEvent = (WotValidationConfigModified) event;
        assertThat(modifiedEvent.getEntityId().toString()).isEqualTo(configId.toString());

        final ModifyWotValidationConfigResponse response = responseCaptor.getValue();
        assertThat(response).isNotNull();
    }

    @Test
    public void testPreviousEntityTag() {
        // Given
        final WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        final DittoHeaders headers = DittoHeaders.empty();
        final WotValidationConfig existingConfig = WotValidationConfig.of(
                configId,
                true,
                false,
                ThingValidationConfig.of(
                    ThingValidationEnforceConfig.of(true, true, true, true, true),
                    ThingValidationForbidConfig.of(false, true, false, true)
                ),
                FeatureValidationConfig.of(
                    FeatureValidationEnforceConfig.of(true, false, true, false, true, false, true),
                    FeatureValidationForbidConfig.of(false, true, false, true, false, true)
                ),
                Collections.emptyList(),
                WotValidationConfigRevision.of(1L),
                Instant.now(),
                Instant.now(),
                false,
                null
        );
        final ModifyWotValidationConfig command = ModifyWotValidationConfig.of(configId, existingConfig, headers);

        // When
        final Optional<EntityTag> entityTag = underTest.previousEntityTag(command, existingConfig);

        // Then
        assertThat(entityTag).isPresent();
        assertThat(entityTag.get().toString()).isEqualTo("\"rev:1\"");
    }

    @Test
    public void testNextEntityTag() {
        // Given
        final WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        final DittoHeaders headers = DittoHeaders.empty();
        final WotValidationConfig newConfig = WotValidationConfig.of(
                configId,
                true,
                false,
                ThingValidationConfig.of(
                    ThingValidationEnforceConfig.of(true, true, true, true, true),
                    ThingValidationForbidConfig.of(false, true, false, true)
                ),
                FeatureValidationConfig.of(
                    FeatureValidationEnforceConfig.of(true, false, true, false, true, false, true),
                    FeatureValidationForbidConfig.of(false, true, false, true, false, true)
                ),
                Collections.emptyList(),
                WotValidationConfigRevision.of(2L),
                Instant.now(),
                Instant.now(),
                false,
                null
        );
        final ModifyWotValidationConfig command = ModifyWotValidationConfig.of(configId, newConfig, headers);

        // When
        final Optional<EntityTag> entityTag = underTest.nextEntityTag(command, newConfig);

        // Then
        assertThat(entityTag).isPresent();
        assertThat(entityTag.get().toString()).isEqualTo("\"rev:2\"");
    }
} 