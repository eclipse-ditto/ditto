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

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.things.model.devops.ConfigOverrides;
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationEnforceConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationForbidConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationEnforceConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationForbidConfig;
import org.eclipse.ditto.things.model.devops.ValidationContext;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.eclipse.ditto.things.model.devops.commands.RetrieveDynamicConfigSection;
import org.eclipse.ditto.things.model.devops.commands.RetrieveWotValidationConfigResponse;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test for {@link RetrieveDynamicConfigSectionStrategy}.
 */
public final class RetrieveDynamicConfigSectionStrategyTest {

    private RetrieveDynamicConfigSectionStrategy underTest;
    private ActorSystem actorSystem;
    private CommandStrategy.Context<WotValidationConfigId> context;

    @Mock
    private DittoDiagnosticLoggingAdapter logger;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        actorSystem = mock(ActorSystem.class);
        context = mock(CommandStrategy.Context.class);
        
        // Set up logger mock
        when(logger.withCorrelationId(any(WithDittoHeaders.class))).thenReturn(logger);
        when(logger.withCorrelationId(any(DittoHeaders.class))).thenReturn(logger);
        when(logger.withCorrelationId(any(CharSequence.class))).thenReturn(logger);
        when(context.getLog()).thenReturn(logger);
        
        underTest = new RetrieveDynamicConfigSectionStrategy();
    }

    @Test
    public void retrieveDynamicConfigSection() {
        // Given
        final WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        final DittoHeaders headers = DittoHeaders.empty();
        final ValidationContext validationContext = ValidationContext.of(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        final ConfigOverrides configOverrides = ConfigOverrides.of(true, false, null, null);
        final DynamicValidationConfig dynamicSection = DynamicValidationConfig.of("scope1", validationContext, configOverrides);
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
                Collections.singletonList(dynamicSection),
                WotValidationConfigRevision.of(1L),
                Instant.now(),
                Instant.now(),
                false,
                null
        );
        final RetrieveDynamicConfigSection command = RetrieveDynamicConfigSection.of(configId, "scope1", headers);

        // When
        final Result<WotValidationConfigEvent<?>> result = underTest.apply(context, existingConfig, 1L, command);

        // Then
        final ArgumentCaptor<RetrieveWotValidationConfigResponse> responseCaptor = ArgumentCaptor.forClass(RetrieveWotValidationConfigResponse.class);
        final ResultVisitor<WotValidationConfigEvent<?>> visitor = mock(ResultVisitor.class);

        result.accept(visitor, null);

        verify(visitor).onQuery(eq(command), responseCaptor.capture());

        final RetrieveWotValidationConfigResponse response = responseCaptor.getValue();
        assertThat(response).isNotNull();
        assertThat(response.getValidationConfig().asObject().getValue("scopeId").get().asString()).isEqualTo("scope1");
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
        final RetrieveDynamicConfigSection command = RetrieveDynamicConfigSection.of(configId, "section1", headers);

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
        final RetrieveDynamicConfigSection command = RetrieveDynamicConfigSection.of(configId, "section1", headers);

        // When
        final Optional<EntityTag> entityTag = underTest.nextEntityTag(command, newConfig);

        // Then
        assertThat(entityTag).isPresent();
        assertThat(entityTag.get().toString()).isEqualTo("\"rev:2\"");
    }
} 