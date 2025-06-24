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
package org.eclipse.ditto.things.model.devops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.jupiter.api.Test;

class ImmutableWotValidationConfigTest {

    @Test
    void testOfAndGetters() {
        // Test with minimal data
        WotValidationConfigId configId = WotValidationConfigId.of("test:config");
        ImmutableWotValidationConfig config = ImmutableWotValidationConfig.of(
                configId, null, null, null, null, Collections.emptyList(), 
                WotValidationConfigRevision.of(1L), null, null, false, null
        );

        assertEquals(configId, config.getConfigId());
        assertFalse(config.isEnabled().isPresent());
        assertFalse(config.logWarningInsteadOfFailingApiCalls().isPresent());
        assertFalse(config.getThingConfig().isPresent());
        assertFalse(config.getFeatureConfig().isPresent());
        assertTrue(config.getDynamicConfigs().isEmpty());
        assertEquals(WotValidationConfigRevision.of(1L), config.getRevision().get());
        assertFalse(config.getCreated().isPresent());
        assertFalse(config.getModified().isPresent());
        assertFalse(config.isDeleted());
        assertFalse(config.getMetadata().isPresent());
    }

    @Test
    void testWithNonEmptyData() {
        // Create a validation context for dynamic config
        Map<String, String> headerPattern = new HashMap<>();
        headerPattern.put("Authorization", "Bearer *");
        List<Map<String, String>> dittoHeadersPatterns = Collections.singletonList(headerPattern);
        List<String> thingPatterns = Arrays.asList("thing:.*", "thing:special");
        List<String> featurePatterns = Arrays.asList("feature:.*", "feature:extra");
        String scopeId = "testScope";
        ImmutableValidationContext validationContext = ImmutableValidationContext.of(
                dittoHeadersPatterns, thingPatterns, featurePatterns
        );

        // Create thing validation config
        ImmutableThingValidationEnforceConfig thingEnforce = ImmutableThingValidationEnforceConfig.of(
                true,  // enforceThingDescriptionModification
                true,  // enforceAttributes
                true,  // enforceInboxMessagesInput
                true,  // enforceInboxMessagesOutput
                true   // enforceOutboxMessages
        );
        ImmutableThingValidationForbidConfig thingForbid = ImmutableThingValidationForbidConfig.of(
                false,  // forbidThingDescriptionDeletion
                false,  // forbidNonModeledAttributes
                false,  // forbidNonModeledInboxMessages
                false   // forbidNonModeledOutboxMessages
        );
        ImmutableThingValidationConfig thingConfig = ImmutableThingValidationConfig.of(
                thingEnforce, thingForbid
        );

        // Create feature validation config
        ImmutableFeatureValidationEnforceConfig featureEnforce = ImmutableFeatureValidationEnforceConfig.of(
                true,  // enforceFeatureDescriptionModification
                true,  // enforcePresenceOfModeledFeatures
                true,  // enforceProperties
                true,  // enforceDesiredProperties
                true,  // enforceInboxMessagesInput
                true,  // enforceInboxMessagesOutput
                true   // enforceOutboxMessages
        );
        ImmutableFeatureValidationForbidConfig featureForbid = ImmutableFeatureValidationForbidConfig.of(
                false,  // forbidFeatureDescriptionDeletion
                false,  // forbidNonModeledFeatures
                false,  // forbidNonModeledProperties
                false,  // forbidNonModeledDesiredProperties
                false,  // forbidNonModeledInboxMessages
                false   // forbidNonModeledOutboxMessages
        );
        ImmutableFeatureValidationConfig featureConfig = ImmutableFeatureValidationConfig.of(
                featureEnforce, featureForbid
        );

        // Create config overrides for dynamic config
        ImmutableConfigOverrides configOverrides = ImmutableConfigOverrides.of(
                true, false, thingConfig, featureConfig
        );

        // Create dynamic config
        ImmutableDynamicValidationConfig dynamicConfig = ImmutableDynamicValidationConfig.of(
                "scope1", validationContext, configOverrides
        );

        // Create full config with all fields set
        WotValidationConfigId configId = WotValidationConfigId.of("test:config");
        Instant now = Instant.now();
        Metadata metadata = Metadata.newBuilder().set("key", JsonValue.of("value")).build();
        
        ImmutableWotValidationConfig config = ImmutableWotValidationConfig.of(
                configId,
                true,
                false,
                thingConfig,
                featureConfig,
                Collections.singletonList(dynamicConfig),
                WotValidationConfigRevision.of(1L),
                now,
                now,
                false,
                metadata
        );

        // Verify all fields
        assertEquals(configId, config.getConfigId());
        assertTrue(config.isEnabled().orElse(false));
        assertFalse(config.logWarningInsteadOfFailingApiCalls().orElse(true));
        assertEquals(thingConfig, config.getThingConfig().orElse(null));
        assertEquals(featureConfig, config.getFeatureConfig().orElse(null));
        assertEquals(1, config.getDynamicConfigs().size());
        assertEquals(dynamicConfig, config.getDynamicConfigs().get(0));
        assertEquals(WotValidationConfigRevision.of(1L), config.getRevision().get());
        assertEquals(now, config.getCreated().orElse(null));
        assertEquals(now, config.getModified().orElse(null));
        assertFalse(config.isDeleted());
        assertEquals(metadata, config.getMetadata().orElse(null));
    }

    @Test
    void testImmutability() {
        // Test immutability of dynamic config list
        List<ImmutableDynamicValidationConfig> dynamicConfigs = new ArrayList<>();
        dynamicConfigs.add(ImmutableDynamicValidationConfig.of("scope1", null, null));
        
        ImmutableWotValidationConfig config = ImmutableWotValidationConfig.of(
                WotValidationConfigId.of("ditto:global"), null, null, null, null,
                Collections.unmodifiableList(dynamicConfigs), 
                WotValidationConfigRevision.of(1L), null, null, false, null
        );

        assertThrows(UnsupportedOperationException.class, () -> config.getDynamicConfigs().add(null));
        assertThrows(UnsupportedOperationException.class, () -> config.getDynamicConfigs().remove(0));
        assertThrows(UnsupportedOperationException.class, () -> config.getDynamicConfigs().clear());
    }

    @Test
    void testEqualsAndHashCode() {
        WotValidationConfigId configId = WotValidationConfigId.of("ditto:global");

        // Create thing validation config
        ImmutableThingValidationEnforceConfig thingEnforce = ImmutableThingValidationEnforceConfig.of(
                true, true, true, true, true
        );
        ImmutableThingValidationForbidConfig thingForbid = ImmutableThingValidationForbidConfig.of(
                false, false, false, false
        );
        ImmutableThingValidationConfig thingConfig = ImmutableThingValidationConfig.of(
                thingEnforce, thingForbid
        );

        // Create feature validation config
        ImmutableFeatureValidationEnforceConfig featureEnforce = ImmutableFeatureValidationEnforceConfig.of(
                true, true, true, true, true, true, true
        );
        ImmutableFeatureValidationForbidConfig featureForbid = ImmutableFeatureValidationForbidConfig.of(
                false, false, false, false, false, false
        );
        ImmutableFeatureValidationConfig featureConfig = ImmutableFeatureValidationConfig.of(
                featureEnforce, featureForbid
        );

        Instant now = Instant.now();

        ImmutableWotValidationConfig config1 = ImmutableWotValidationConfig.of(
                configId, true, false, thingConfig, featureConfig,
                Collections.emptyList(), WotValidationConfigRevision.of(1L),
                now, now, false, null
        );

        ImmutableWotValidationConfig config2 = ImmutableWotValidationConfig.of(
                configId, true, false, thingConfig, featureConfig,
                Collections.emptyList(), WotValidationConfigRevision.of(1L),
                now, now, false, null
        );

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());

        // Test inequality
        ImmutableWotValidationConfig differentConfig = ImmutableWotValidationConfig.of(
                WotValidationConfigId.of("ditto:dx"), true, false, thingConfig, featureConfig,
                Collections.emptyList(), WotValidationConfigRevision.of(1L),
                now, now, false, null
        );

        assertNotEquals(config1, differentConfig);
        assertNotEquals(config1.hashCode(), differentConfig.hashCode());
    }

    @Test
    void testSetRevision() {
        ImmutableWotValidationConfig config = ImmutableWotValidationConfig.of(
                WotValidationConfigId.of("ditto:global"), null, null, null, null,
                Collections.emptyList(), WotValidationConfigRevision.of(1L), 
                null, null, false, null
        );
        
        ImmutableWotValidationConfig updated = config.setRevision(WotValidationConfigRevision.of(42L));
        assertEquals(WotValidationConfigRevision.of(42L), updated.getRevision().orElse(null));
        assertNotEquals(config, updated);
    }

    @Test
    void testToJsonAndFromJsonRoundtrip() {
        // Create a complex config with all fields set
        WotValidationConfigId configId = WotValidationConfigId.of("test:config");
        
        // Create thing validation config
        ImmutableThingValidationEnforceConfig thingEnforce = ImmutableThingValidationEnforceConfig.of(
                true, true, true, true, true
        );
        ImmutableThingValidationForbidConfig thingForbid = ImmutableThingValidationForbidConfig.of(
                false, false, false, false
        );
        ImmutableThingValidationConfig thingConfig = ImmutableThingValidationConfig.of(
                thingEnforce, thingForbid
        );

        // Create feature validation config
        ImmutableFeatureValidationEnforceConfig featureEnforce = ImmutableFeatureValidationEnforceConfig.of(
                true, true, true, true, true, true, true
        );
        ImmutableFeatureValidationForbidConfig featureForbid = ImmutableFeatureValidationForbidConfig.of(
                false, false, false, false, false, false
        );
        ImmutableFeatureValidationConfig featureConfig = ImmutableFeatureValidationConfig.of(
                featureEnforce, featureForbid
        );
        
        // Create dynamic config
        Map<String, String> headerPattern = new HashMap<>();
        headerPattern.put("Authorization", "Bearer *");
        ImmutableValidationContext validationContext = ImmutableValidationContext.of(
                Collections.singletonList(headerPattern),
                Collections.singletonList("thing:.*"),
                Collections.singletonList("feature:.*")
        );
        ImmutableConfigOverrides configOverrides = ImmutableConfigOverrides.of(
                true, false, thingConfig, featureConfig
        );
        ImmutableDynamicValidationConfig dynamicConfig = ImmutableDynamicValidationConfig.of(
                "scope1", validationContext, configOverrides
        );

        Instant now = Instant.now();
        Metadata metadata = Metadata.newBuilder().set("key", JsonValue.of("value")).build();

        ImmutableWotValidationConfig original = ImmutableWotValidationConfig.of(
                configId,
                true,
                false,
                thingConfig,
                featureConfig,
                Collections.singletonList(dynamicConfig),
                WotValidationConfigRevision.of(1L),
                now,
                now,
                false,
                metadata
        );

        // Test roundtrip
        JsonObject json = original.toJson();
        ImmutableWotValidationConfig parsed = ImmutableWotValidationConfig.fromJson(json);

        assertEquals(original, parsed);
        assertEquals(original.getConfigId(), parsed.getConfigId());
        assertEquals(original.isEnabled(), parsed.isEnabled());
        assertEquals(original.logWarningInsteadOfFailingApiCalls(), parsed.logWarningInsteadOfFailingApiCalls());
        assertEquals(original.getThingConfig(), parsed.getThingConfig());
        assertEquals(original.getFeatureConfig(), parsed.getFeatureConfig());
        assertEquals(original.getDynamicConfigs(), parsed.getDynamicConfigs());
        assertEquals(original.getRevision(), parsed.getRevision());
        assertEquals(original.getCreated(), parsed.getCreated());
        assertEquals(original.getModified(), parsed.getModified());
        assertEquals(original.isDeleted(), parsed.isDeleted());
        assertEquals(original.getMetadata(), parsed.getMetadata());
    }

    @Test
    void testNullAndEmptyHandling() {
        ImmutableWotValidationConfig config = ImmutableWotValidationConfig.of(
                WotValidationConfigId.of("ditto:global"), null, null, null, null,
                Collections.emptyList(), WotValidationConfigRevision.of(0L), 
                null, null, null, null
        );

        assertFalse(config.isEnabled().isPresent());
        assertFalse(config.logWarningInsteadOfFailingApiCalls().isPresent());
        assertFalse(config.getThingConfig().isPresent());
        assertFalse(config.getFeatureConfig().isPresent());
        assertTrue(config.getDynamicConfigs().isEmpty());
        assertFalse(config.getCreated().isPresent());
        assertFalse(config.getModified().isPresent());
        assertFalse(config.getMetadata().isPresent());
    }

    @Test
    void testJsonFieldDefinitions() {
        // Create thing validation config
        ImmutableThingValidationEnforceConfig thingEnforce = ImmutableThingValidationEnforceConfig.of(
                true, true, true, true, true
        );
        ImmutableThingValidationForbidConfig thingForbid = ImmutableThingValidationForbidConfig.of(
                false, false, false, false
        );
        ImmutableThingValidationConfig thingConfig = ImmutableThingValidationConfig.of(
                thingEnforce, thingForbid
        );

        // Create feature validation config
        ImmutableFeatureValidationEnforceConfig featureEnforce = ImmutableFeatureValidationEnforceConfig.of(
                true, true, true, true, true, true, true
        );
        ImmutableFeatureValidationForbidConfig featureForbid = ImmutableFeatureValidationForbidConfig.of(
                false, false, false, false, false, false
        );
        ImmutableFeatureValidationConfig featureConfig = ImmutableFeatureValidationConfig.of(
                featureEnforce, featureForbid
        );

        JsonObject json = JsonFactory.newObjectBuilder()
                .set("configId", "test:config")
                .set("enabled", true)
                .set("log-warning-instead-of-failing-api-calls", false)
                .set("thing", thingConfig.toJson())
                .set("feature", featureConfig.toJson())
                .set("dynamic-config", JsonFactory.newArrayBuilder().build())
                .set("_revision", 1L)
                .set("_created", "2024-01-01T00:00:00Z")
                .set("_modified", "2024-01-01T00:00:00Z")
                .set("_deleted", false)
                .set("_metadata", JsonFactory.newObjectBuilder().build())
                .build();

        ImmutableWotValidationConfig config = ImmutableWotValidationConfig.fromJson(json);

        assertEquals("test:config", config.getConfigId().toString());
        assertTrue(config.isEnabled().orElse(false));
        assertFalse(config.logWarningInsteadOfFailingApiCalls().orElse(true));
        assertTrue(config.getThingConfig().isPresent());
        assertTrue(config.getFeatureConfig().isPresent());
        assertTrue(config.getDynamicConfigs().isEmpty());
        assertEquals(WotValidationConfigRevision.of(1L), config.getRevision().get());
        assertTrue(config.getCreated().isPresent());
        assertTrue(config.getModified().isPresent());
        assertFalse(config.isDeleted());
        assertTrue(config.getMetadata().isPresent());
    }
} 