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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Collections;

import org.eclipse.ditto.things.model.devops.FeatureValidationConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationEnforceConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationForbidConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationEnforceConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationForbidConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.eclipse.ditto.wot.api.config.DefaultTmValidationConfig;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;
import org.junit.Test;

public class WotValidationConfigUtilsTest {

    @Test
    public void mergeConfigs_returnsStaticConfigIfEntityNull() {
        String configString = "tm-model-validation {\n" +
                "  enabled = true\n" +
                "  log-warning-instead-of-failing-api-calls = false\n" +
                "  thing {\n" +
                "    enforce {\n" +
                "      thing-description-modification = true\n" +
                "      attributes = true\n" +
                "      inbox-messages-input = true\n" +
                "      inbox-messages-output = true\n" +
                "      outbox-messages = true\n" +
                "    }\n" +
                "    forbid {\n" +
                "      thing-description-deletion = true\n" +
                "      non-modeled-attributes = true\n" +
                "      non-modeled-inbox-messages = true\n" +
                "      non-modeled-outbox-messages = true\n" +
                "    }\n" +
                "  }\n" +
                "  feature {\n" +
                "    enforce {\n" +
                "      feature-description-modification = true\n" +
                "      presence-of-modeled-features = true\n" +
                "      properties = true\n" +
                "      desired-properties = true\n" +
                "      inbox-messages-input = true\n" +
                "      inbox-messages-output = true\n" +
                "      outbox-messages = true\n" +
                "    }\n" +
                "    forbid {\n" +
                "      feature-description-deletion = true\n" +
                "      non-modeled-features = true\n" +
                "      non-modeled-properties = true\n" +
                "      non-modeled-desired-properties = true\n" +
                "      non-modeled-inbox-messages = true\n" +
                "      non-modeled-outbox-messages = true\n" +
                "    }\n" +
                "  }\n" +
                "  dynamic-configuration = []\n" +
                "}";
        TmValidationConfig staticConfig = DefaultTmValidationConfig.of(com.typesafe.config.ConfigFactory.parseString(configString));

        WotValidationConfig merged = WotValidationConfigUtils.mergeConfigs(null, staticConfig);
        assertThat(merged.isEnabled()).contains(true);
        assertThat(merged.logWarningInsteadOfFailingApiCalls()).contains(false);
        assertThat(merged.getThingConfig()).isPresent();
        assertThat(merged.getFeatureConfig()).isPresent();
        assertThat(merged.getThingConfig().get().getEnforce().get().isThingDescriptionModification().get()).isEqualTo(true);
        assertThat(merged.getFeatureConfig().get().getEnforce().get().isFeatureDescriptionModification().get()).isEqualTo(true);
    }

    @Test
    public void mergeConfigs_mergesEntityAndStaticConfig() {
        ThingValidationEnforceConfig thingEnforce = ThingValidationEnforceConfig.of(false, false, false, false, false);
        ThingValidationForbidConfig thingForbid = ThingValidationForbidConfig.of(false, false, false, false);
        ThingValidationConfig thingConfig = ThingValidationConfig.of(thingEnforce, thingForbid);
        FeatureValidationEnforceConfig featureEnforce = FeatureValidationEnforceConfig.of(false, false, false, false, false, false, false);
        FeatureValidationForbidConfig featureForbid = FeatureValidationForbidConfig.of(false, false, false, false, false, false);
        FeatureValidationConfig featureConfig = FeatureValidationConfig.of(featureEnforce, featureForbid);
        WotValidationConfig entity = WotValidationConfig.of(
                WotValidationConfigId.of("test:1"),
                false,
                true,
                thingConfig,
                featureConfig,
                Collections.emptyList(),
                WotValidationConfigRevision.of(1L),
                Instant.now(),
                Instant.now(),
                false,
                null
        );
        String configString = "tm-model-validation {\n" +
                "  enabled = true\n" +
                "  log-warning-instead-of-failing-api-calls = false\n" +
                "  thing {\n" +
                "    enforce {\n" +
                "      thing-description-modification = true\n" +
                "      attributes = true\n" +
                "      inbox-messages-input = true\n" +
                "      inbox-messages-output = true\n" +
                "      outbox-messages = true\n" +
                "    }\n" +
                "    forbid {\n" +
                "      thing-description-deletion = true\n" +
                "      non-modeled-attributes = true\n" +
                "      non-modeled-inbox-messages = true\n" +
                "      non-modeled-outbox-messages = true\n" +
                "    }\n" +
                "  }\n" +
                "  feature {\n" +
                "    enforce {\n" +
                "      feature-description-modification = true\n" +
                "      presence-of-modeled-features = true\n" +
                "      properties = true\n" +
                "      desired-properties = true\n" +
                "      inbox-messages-input = true\n" +
                "      inbox-messages-output = true\n" +
                "      outbox-messages = true\n" +
                "    }\n" +
                "    forbid {\n" +
                "      feature-description-deletion = true\n" +
                "      non-modeled-features = true\n" +
                "      non-modeled-properties = true\n" +
                "      non-modeled-desired-properties = true\n" +
                "      non-modeled-inbox-messages = true\n" +
                "      non-modeled-outbox-messages = true\n" +
                "    }\n" +
                "  }\n" +
                "  dynamic-configuration = []\n" +
                "}";
        TmValidationConfig staticConfig = DefaultTmValidationConfig.of(com.typesafe.config.ConfigFactory.parseString(configString));

        WotValidationConfig merged = WotValidationConfigUtils.mergeConfigs(entity, staticConfig);
        assertThat(merged.isEnabled()).contains(false); // entity takes precedence
        assertThat(merged.logWarningInsteadOfFailingApiCalls()).contains(true);
        assertThat(merged.getThingConfig()).isPresent();
        assertThat(merged.getFeatureConfig()).isPresent();
        assertThat(merged.getThingConfig().get().getEnforce().get().isThingDescriptionModification().get()).isEqualTo(false);
        assertThat(merged.getFeatureConfig().get().getEnforce().get().isFeatureDescriptionModification().get()).isEqualTo(false);
    }

    @Test
    public void mergeConfigs_throwsOnNullStaticConfig() {
        assertThatThrownBy(() -> WotValidationConfigUtils.mergeConfigs(null, null)).isInstanceOf(NullPointerException.class);
    }
}