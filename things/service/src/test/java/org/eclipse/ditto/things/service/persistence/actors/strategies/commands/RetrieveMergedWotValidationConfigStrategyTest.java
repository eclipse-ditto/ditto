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
import org.eclipse.ditto.things.model.devops.commands.RetrieveMergedWotValidationConfig;
import org.eclipse.ditto.things.model.devops.commands.RetrieveMergedWotValidationConfigResponse;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test for {@link RetrieveMergedWotValidationConfigStrategy}.
 */
public final class RetrieveMergedWotValidationConfigStrategyTest {

    private RetrieveMergedWotValidationConfigStrategy underTest;
    private TmValidationConfig staticConfig;
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

        // Mock static config
        staticConfig = mock(TmValidationConfig.class);
        when(staticConfig.isEnabled()).thenReturn(true);
        when(staticConfig.logWarningInsteadOfFailingApiCalls()).thenReturn(false);

        // Mock thing validation config with enforce/forbid
        final org.eclipse.ditto.wot.validation.config.ThingValidationConfig thingValidationConfig = mock(org.eclipse.ditto.wot.validation.config.ThingValidationConfig.class);
        final ThingValidationConfig devopsThingValidationConfig = mock(ThingValidationConfig.class);
        when(devopsThingValidationConfig.getEnforce()).thenReturn(Optional.of(ThingValidationEnforceConfig.of(true, true, true, true, true)));
        when(devopsThingValidationConfig.getForbid()).thenReturn(Optional.of(ThingValidationForbidConfig.of(false, true, false, true)));
        when(staticConfig.getThingValidationConfig()).thenReturn(thingValidationConfig);

        // Mock feature validation config with enforce/forbid
        final org.eclipse.ditto.wot.validation.config.FeatureValidationConfig featureValidationConfig = mock(org.eclipse.ditto.wot.validation.config.FeatureValidationConfig.class);
        final FeatureValidationConfig devopsFeatureValidationConfig = mock(FeatureValidationConfig.class);
        when(devopsFeatureValidationConfig.getEnforce()).thenReturn(Optional.of(FeatureValidationEnforceConfig.of(true, false, true, false, true, false, true)));
        when(devopsFeatureValidationConfig.getForbid()).thenReturn(Optional.of(FeatureValidationForbidConfig.of(false, true, false, true, false, true)));
        when(staticConfig.getFeatureValidationConfig()).thenReturn(featureValidationConfig);

        // Mock dynamic config
        when(staticConfig.getDynamicConfigs()).thenReturn(Collections.emptyList());

        underTest = new RetrieveMergedWotValidationConfigStrategy(staticConfig);
    }

    @Test
    public void retrieveMergedConfig() {
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
        final RetrieveMergedWotValidationConfig command = RetrieveMergedWotValidationConfig.of(configId, headers);

        // When
        final Result<WotValidationConfigEvent<?>> result = underTest.apply(context, existingConfig, 1L, command);

        // Then
        final ArgumentCaptor<RetrieveMergedWotValidationConfigResponse> responseCaptor = ArgumentCaptor.forClass(RetrieveMergedWotValidationConfigResponse.class);
        final ResultVisitor<WotValidationConfigEvent<?>> visitor = mock(ResultVisitor.class);

        result.accept(visitor, null);

        verify(visitor).onQuery(eq(command), responseCaptor.capture());

        final RetrieveMergedWotValidationConfigResponse response = responseCaptor.getValue();
        assertThat(response).isNotNull();
        final JsonObject mergedConfig = response.getConfig().toJson();

        // Verify merged config values
        assertThat(mergedConfig.getValue("enabled").get().asBoolean()).isTrue();
        assertThat(mergedConfig.getValue("log-warning-instead-of-failing-api-calls").get().asBoolean()).isFalse();

        // Verify thing validation config
        final JsonObject thingConfig = mergedConfig.getValue("thing").get().asObject();
        final JsonObject thingEnforce = thingConfig.getValue("enforce").get().asObject();
        final JsonObject thingForbid = thingConfig.getValue("forbid").get().asObject();
        assertThat(thingEnforce.getValue("thing-description-modification").get().asBoolean()).isTrue();
        assertThat(thingEnforce.getValue("attributes").get().asBoolean()).isTrue();
        assertThat(thingForbid.getValue("thing-description-deletion").get().asBoolean()).isFalse();
        assertThat(thingForbid.getValue("non-modeled-inbox-messages").get().asBoolean()).isFalse();

        // Verify feature validation config
        final JsonObject featureConfig = mergedConfig.getValue("feature").get().asObject();
        final JsonObject featureEnforce = featureConfig.getValue("enforce").get().asObject();
        final JsonObject featureForbid = featureConfig.getValue("forbid").get().asObject();
        assertThat(featureEnforce.getValue("feature-description-modification").get().asBoolean()).isTrue();
        assertThat(featureEnforce.getValue("presence-of-modeled-features").get().asBoolean()).isFalse();
        assertThat(featureForbid.getValue("feature-description-deletion").get().asBoolean()).isFalse();
        assertThat(featureForbid.getValue("non-modeled-features").get().asBoolean()).isTrue();
    }

    @Test
    public void testPreviousEntityTag() {
        // Given
        final WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        final DittoHeaders headers = DittoHeaders.empty();
        final ThingValidationEnforceConfig thingEnforce = ThingValidationEnforceConfig.of(true, true, true, true, true);
        final ThingValidationForbidConfig thingForbid = ThingValidationForbidConfig.of(false, true, false, true);
        final FeatureValidationEnforceConfig featureEnforce = FeatureValidationEnforceConfig.of(true, false, true, false, true, false, true);
        final FeatureValidationForbidConfig featureForbid = FeatureValidationForbidConfig.of(false, true, false, true, false, true);
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
        final RetrieveMergedWotValidationConfig command = RetrieveMergedWotValidationConfig.of(configId, headers);

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
        final RetrieveMergedWotValidationConfig command = RetrieveMergedWotValidationConfig.of(configId, headers);

        // When
        final Optional<EntityTag> entityTag = underTest.nextEntityTag(command, newConfig);

        // Then
        assertThat(entityTag).isPresent();
        assertThat(entityTag.get().toString()).isEqualTo("\"rev:2\"");
    }
} 