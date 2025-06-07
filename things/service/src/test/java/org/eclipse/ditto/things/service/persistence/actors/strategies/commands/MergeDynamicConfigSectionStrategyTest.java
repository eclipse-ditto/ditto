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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.eclipse.ditto.things.model.devops.commands.MergeDynamicConfigSection;
import org.eclipse.ditto.things.model.devops.events.DynamicConfigSectionMerged;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.persistentactors.commands.DefaultContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Collections;

/**
 * Unit test for {@link MergeDynamicConfigSectionStrategy}.
 */
public final class MergeDynamicConfigSectionStrategyTest {

    private MergeDynamicConfigSectionStrategy underTest;
    private WotValidationConfigDData ddata;
    private ActorSystem actorSystem;
    private CommandStrategy.Context<WotValidationConfigId> context;

    @Mock
    private DittoDiagnosticLoggingAdapter logger;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        actorSystem = mock(ActorSystem.class);
        ddata = mock(WotValidationConfigDData.class);

        // Set up logger mock
        Mockito.lenient().when(logger.withCorrelationId(Mockito.any(DittoHeaders.class))).thenReturn(logger);
        Mockito.lenient().when(logger.withCorrelationId(Mockito.any(WithDittoHeaders.class))).thenReturn(logger);
        Mockito.lenient().when(logger.withCorrelationId(Mockito.any(CharSequence.class))).thenReturn(logger);
        Mockito.lenient().when(logger.isDebugEnabled()).thenReturn(true);

        // Use a valid namespaced entity ID
        final WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        context = DefaultContext.getInstance(configId, logger, actorSystem);

        underTest = new MergeDynamicConfigSectionStrategy(ddata);
    }

    @Test
    public void testMergeNewSection() {
        final DynamicValidationConfig config = DynamicValidationConfig.of(
                "test-scope",
                null, // validationContext
                null  // configOverrides
        );

        final WotValidationConfig entity = WotValidationConfig.of(
                context.getState(),
                true, // enabled
                false, // logWarningInsteadOfFailingApiCalls
                null, // thingConfig
                null, // featureConfig
                Collections.emptyList(), // dynamicConfigs
                WotValidationConfigRevision.of(1L), // revision
                Instant.now(), // created
                Instant.now(), // modified
                false, // deleted
                null  // metadata
        );

        final MergeDynamicConfigSection command = MergeDynamicConfigSection.of(
                context.getState(),
                "test-scope",
                config,
                DittoHeaders.empty()
        );

        when(ddata.add(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        final var result = underTest.apply(context, entity, 1L, command);
        final ArgumentCaptor<DynamicConfigSectionMerged> eventCaptor = ArgumentCaptor.forClass(DynamicConfigSectionMerged.class);
        final var visitor = mock(ResultVisitor.class);

        result.accept(visitor, null);

        verify(visitor).onMutation(any(), eventCaptor.capture(), any(), anyBoolean(), anyBoolean(), any());
        final DynamicConfigSectionMerged event = eventCaptor.getValue();
        assertEquals("test-scope", event.getSectionValue().getScopeId());
        assertEquals(config.toJson(), event.getSectionValue().toJson());
    }

    @Test
    public void testReplaceExistingSection() {
        final DynamicValidationConfig existingConfig = DynamicValidationConfig.of(
                "test-scope",
                null, // validationContext
                null  // configOverrides
        );

        final DynamicValidationConfig newConfig = DynamicValidationConfig.of(
                "test-scope",
                null, // validationContext
                null  // configOverrides
        );

        final WotValidationConfig entity = WotValidationConfig.of(
                context.getState(),
                true, // enabled
                false, // logWarningInsteadOfFailingApiCalls
                null, // thingConfig
                null, // featureConfig
                Collections.singletonList(existingConfig), // dynamicConfigs
                WotValidationConfigRevision.of(1L), // revision
                Instant.now(), // created
                Instant.now(), // modified
                false, // deleted
                null  // metadata
        );

        final MergeDynamicConfigSection command = MergeDynamicConfigSection.of(
                context.getState(),
                "test-scope",
                newConfig,
                DittoHeaders.empty()
        );

        when(ddata.add(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        final var result = underTest.apply(context, entity, 2L, command);
        final ArgumentCaptor<DynamicConfigSectionMerged> eventCaptor = ArgumentCaptor.forClass(DynamicConfigSectionMerged.class);
        final var visitor = mock(ResultVisitor.class);

        result.accept(visitor, null);

        verify(visitor).onMutation(any(), eventCaptor.capture(), any(), anyBoolean(), anyBoolean(), any());
        final DynamicConfigSectionMerged event = eventCaptor.getValue();
        assertEquals("test-scope", event.getSectionValue().getScopeId());
        assertEquals(newConfig.toJson(), event.getSectionValue().toJson());
    }

    @Test
    public void testNullEntityThrows() {
        final DynamicValidationConfig config = DynamicValidationConfig.of(
                "test-scope",
                null, // validationContext
                null  // configOverrides
        );

        final MergeDynamicConfigSection command = MergeDynamicConfigSection.of(
                context.getState(),
                "test-scope",
                config,
                DittoHeaders.empty()
        );

        assertThrows(NullPointerException.class, () -> underTest.apply(null, null, 1L, command));
    }
} 