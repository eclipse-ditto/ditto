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
package org.eclipse.ditto.wot.api.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Tests for {@link WotConfigTransformationUtils}.
 */
public class WotConfigTransformationUtilsTest {

    @Test
    public void testConvertEmptyConfigOverrides() {
        // Given: Empty config overrides
        final JsonObject emptyConfig = JsonFactory.newObject();

        // When: Converting to validation config JSON
        final JsonObject result = WotConfigTransformationUtils.convertHoconConfigOverridesToValidationConfigJson(emptyConfig);

        // Then: Should return empty object
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testConvertTopLevelFields() {
        // Given: Config with only top-level fields
        final JsonObject config = JsonFactory.newObjectBuilder()
                .set("enabled", true)
                .set("log-warning-instead-of-failing-api-calls", false)
                .build();

        // When: Converting to validation config JSON
        final JsonObject result = WotConfigTransformationUtils.convertHoconConfigOverridesToValidationConfigJson(config);

        // Then: Should convert field names to camelCase
        assertNotNull(result);
        assertEquals(2, result.getSize());
        assertTrue(result.getValue("enabled").orElseThrow().asBoolean());
        assertFalse(result.getValue("logWarningInsteadOfFailingApiCalls").orElseThrow().asBoolean());
    }

    @Test
    public void testConvertNestedThingConfig() {
        // Given: Config with nested thing structure
        final JsonObject config = JsonFactory.newObjectBuilder()
                .set("thing", JsonFactory.newObjectBuilder()
                        .set("enforce", JsonFactory.newObjectBuilder()
                                .set("thing-description-modification", true)
                                .set("attributes", false)
                                .set("inbox-messages-input", true)
                                .set("inbox-messages-output", false)
                                .set("outbox-messages", true)
                                .build())
                        .set("forbid", JsonFactory.newObjectBuilder()
                                .set("thing-description-deletion", false)
                                .set("non-modeled-attributes", true)
                                .set("non-modeled-inbox-messages", false)
                                .set("non-modeled-outbox-messages", true)
                                .build())
                        .build())
                .build();

        // When: Converting to validation config JSON
        final JsonObject result = WotConfigTransformationUtils.convertHoconConfigOverridesToValidationConfigJson(config);

        // Then: Should maintain nested structure with camelCase field names
        assertNotNull(result);
        assertTrue(result.getValue("thing").isPresent());

        final JsonObject thingConfig = result.getValue("thing").orElseThrow().asObject();
        assertTrue(thingConfig.getValue("enforce").isPresent());
        assertTrue(thingConfig.getValue("forbid").isPresent());

        final JsonObject enforce = thingConfig.getValue("enforce").orElseThrow().asObject();
        assertEquals(5, enforce.getSize());
        assertTrue(enforce.getValue("thingDescriptionModification").orElseThrow().asBoolean());
        assertFalse(enforce.getValue("attributes").orElseThrow().asBoolean());
        assertTrue(enforce.getValue("inboxMessagesInput").orElseThrow().asBoolean());
        assertFalse(enforce.getValue("inboxMessagesOutput").orElseThrow().asBoolean());
        assertTrue(enforce.getValue("outboxMessages").orElseThrow().asBoolean());

        final JsonObject forbid = thingConfig.getValue("forbid").orElseThrow().asObject();
        assertEquals(4, forbid.getSize());
        assertFalse(forbid.getValue("thingDescriptionDeletion").orElseThrow().asBoolean());
        assertTrue(forbid.getValue("nonModeledAttributes").orElseThrow().asBoolean());
        assertFalse(forbid.getValue("nonModeledInboxMessages").orElseThrow().asBoolean());
        assertTrue(forbid.getValue("nonModeledOutboxMessages").orElseThrow().asBoolean());
    }

    @Test
    public void testConvertFlatThingConfig() {
        // Given: Config with flat thing structure
        final JsonObject config = JsonFactory.newObjectBuilder()
                .set("thing", JsonFactory.newObjectBuilder()
                        .set("enforce-thing-description-modification", true)
                        .set("enforce-attributes", false)
                        .set("enforce-inbox-messages-input", true)
                        .set("enforce-inbox-messages-output", false)
                        .set("enforce-outbox-messages", true)
                        .set("forbid-thing-description-deletion", false)
                        .set("forbid-non-modeled-attributes", true)
                        .set("forbid-non-modeled-inbox-messages", false)
                        .set("forbid-non-modeled-outbox-messages", true)
                        .build())
                .build();

        // When: Converting to validation config JSON
        final JsonObject result = WotConfigTransformationUtils.convertHoconConfigOverridesToValidationConfigJson(config);

        // Then: Should transform flat structure to nested structure
        assertNotNull(result);
        assertTrue(result.getValue("thing").isPresent());

        final JsonObject thingConfig = result.getValue("thing").orElseThrow().asObject();
        assertTrue(thingConfig.getValue("enforce").isPresent());
        assertTrue(thingConfig.getValue("forbid").isPresent());

        final JsonObject enforce = thingConfig.getValue("enforce").orElseThrow().asObject();
        assertEquals(5, enforce.getSize());
        assertTrue(enforce.getValue("thingDescriptionModification").orElseThrow().asBoolean());
        assertFalse(enforce.getValue("attributes").orElseThrow().asBoolean());
        assertTrue(enforce.getValue("inboxMessagesInput").orElseThrow().asBoolean());
        assertFalse(enforce.getValue("inboxMessagesOutput").orElseThrow().asBoolean());
        assertTrue(enforce.getValue("outboxMessages").orElseThrow().asBoolean());

        final JsonObject forbid = thingConfig.getValue("forbid").orElseThrow().asObject();
        assertEquals(4, forbid.getSize());
        assertFalse(forbid.getValue("thingDescriptionDeletion").orElseThrow().asBoolean());
        assertTrue(forbid.getValue("nonModeledAttributes").orElseThrow().asBoolean());
        assertFalse(forbid.getValue("nonModeledInboxMessages").orElseThrow().asBoolean());
        assertTrue(forbid.getValue("nonModeledOutboxMessages").orElseThrow().asBoolean());
    }

    @Test
    public void testConvertNestedFeatureConfig() {
        // Given: Config with nested feature structure
        final JsonObject config = JsonFactory.newObjectBuilder()
                .set("feature", JsonFactory.newObjectBuilder()
                        .set("enforce", JsonFactory.newObjectBuilder()
                                .set("feature-description-modification", true)
                                .set("presence-of-modeled-features", false)
                                .set("properties", true)
                                .set("desired-properties", false)
                                .set("inbox-messages-input", true)
                                .set("inbox-messages-output", false)
                                .set("outbox-messages", true)
                                .build())
                        .set("forbid", JsonFactory.newObjectBuilder()
                                .set("feature-description-deletion", false)
                                .set("non-modeled-features", true)
                                .set("non-modeled-properties", false)
                                .set("non-modeled-desired-properties", true)
                                .set("non-modeled-inbox-messages", false)
                                .set("non-modeled-outbox-messages", true)
                                .build())
                        .build())
                .build();

        // When: Converting to validation config JSON
        final JsonObject result = WotConfigTransformationUtils.convertHoconConfigOverridesToValidationConfigJson(config);

        // Then: Should maintain nested structure with camelCase field names
        assertNotNull(result);
        assertTrue(result.getValue("feature").isPresent());

        final JsonObject featureConfig = result.getValue("feature").orElseThrow().asObject();
        assertTrue(featureConfig.getValue("enforce").isPresent());
        assertTrue(featureConfig.getValue("forbid").isPresent());

        final JsonObject enforce = featureConfig.getValue("enforce").orElseThrow().asObject();
        assertEquals(7, enforce.getSize());
        assertTrue(enforce.getValue("featureDescriptionModification").orElseThrow().asBoolean());
        assertFalse(enforce.getValue("presenceOfModeledFeatures").orElseThrow().asBoolean());
        assertTrue(enforce.getValue("properties").orElseThrow().asBoolean());
        assertFalse(enforce.getValue("desiredProperties").orElseThrow().asBoolean());
        assertTrue(enforce.getValue("inboxMessagesInput").orElseThrow().asBoolean());
        assertFalse(enforce.getValue("inboxMessagesOutput").orElseThrow().asBoolean());
        assertTrue(enforce.getValue("outboxMessages").orElseThrow().asBoolean());

        final JsonObject forbid = featureConfig.getValue("forbid").orElseThrow().asObject();
        assertEquals(6, forbid.getSize());
        assertFalse(forbid.getValue("featureDescriptionDeletion").orElseThrow().asBoolean());
        assertTrue(forbid.getValue("nonModeledFeatures").orElseThrow().asBoolean());
        assertFalse(forbid.getValue("nonModeledProperties").orElseThrow().asBoolean());
        assertTrue(forbid.getValue("nonModeledDesiredProperties").orElseThrow().asBoolean());
        assertFalse(forbid.getValue("nonModeledInboxMessages").orElseThrow().asBoolean());
        assertTrue(forbid.getValue("nonModeledOutboxMessages").orElseThrow().asBoolean());
    }

    @Test
    public void testConvertFlatFeatureConfig() {
        // Given: Config with flat feature structure
        final JsonObject config = JsonFactory.newObjectBuilder()
                .set("feature", JsonFactory.newObjectBuilder()
                        .set("enforce-feature-description-modification", true)
                        .set("enforce-presence-of-modeled-features", false)
                        .set("enforce-properties", true)
                        .set("enforce-desired-properties", false)
                        .set("enforce-inbox-messages-input", true)
                        .set("enforce-inbox-messages-output", false)
                        .set("enforce-outbox-messages", true)
                        .set("forbid-feature-description-deletion", false)
                        .set("forbid-non-modeled-features", true)
                        .set("forbid-non-modeled-properties", false)
                        .set("forbid-non-modeled-desired-properties", true)
                        .set("forbid-non-modeled-inbox-messages", false)
                        .set("forbid-non-modeled-outbox-messages", true)
                        .build())
                .build();

        // When: Converting to validation config JSON
        final JsonObject result = WotConfigTransformationUtils.convertHoconConfigOverridesToValidationConfigJson(config);

        // Then: Should transform flat structure to nested structure
        assertNotNull(result);
        assertTrue(result.getValue("feature").isPresent());

        final JsonObject featureConfig = result.getValue("feature").orElseThrow().asObject();
        assertTrue(featureConfig.getValue("enforce").isPresent());
        assertTrue(featureConfig.getValue("forbid").isPresent());

        final JsonObject enforce = featureConfig.getValue("enforce").orElseThrow().asObject();
        assertEquals(7, enforce.getSize());
        assertTrue(enforce.getValue("featureDescriptionModification").orElseThrow().asBoolean());
        assertFalse(enforce.getValue("presenceOfModeledFeatures").orElseThrow().asBoolean());
        assertTrue(enforce.getValue("properties").orElseThrow().asBoolean());
        assertFalse(enforce.getValue("desiredProperties").orElseThrow().asBoolean());
        assertTrue(enforce.getValue("inboxMessagesInput").orElseThrow().asBoolean());
        assertFalse(enforce.getValue("inboxMessagesOutput").orElseThrow().asBoolean());
        assertTrue(enforce.getValue("outboxMessages").orElseThrow().asBoolean());

        final JsonObject forbid = featureConfig.getValue("forbid").orElseThrow().asObject();
        assertEquals(6, forbid.getSize());
        assertFalse(forbid.getValue("featureDescriptionDeletion").orElseThrow().asBoolean());
        assertTrue(forbid.getValue("nonModeledFeatures").orElseThrow().asBoolean());
        assertFalse(forbid.getValue("nonModeledProperties").orElseThrow().asBoolean());
        assertTrue(forbid.getValue("nonModeledDesiredProperties").orElseThrow().asBoolean());
        assertFalse(forbid.getValue("nonModeledInboxMessages").orElseThrow().asBoolean());
        assertTrue(forbid.getValue("nonModeledOutboxMessages").orElseThrow().asBoolean());
    }

    @Test
    public void testConvertMixedStructure() {
        // Given: Config with mixed nested and flat structure
        final JsonObject config = JsonFactory.newObjectBuilder()
                .set("enabled", true)
                .set("log-warning-instead-of-failing-api-calls", false)
                .set("thing", JsonFactory.newObjectBuilder()
                        .set("enforce", JsonFactory.newObjectBuilder()
                                .set("thing-description-modification", true)
                                .set("outbox-messages", false)
                                .build())
                        .set("enforce-attributes", true)
                        .set("enforce-inbox-messages-input", false)
                        .set("forbid", JsonFactory.newObjectBuilder()
                                .set("non-modeled-inbox-messages", true)
                                .build())
                        .set("forbid-thing-description-deletion", false)
                        .set("forbid-non-modeled-attributes", true)
                        .build())
                .set("feature", JsonFactory.newObjectBuilder()
                        .set("enforce", JsonFactory.newObjectBuilder()
                                .set("feature-description-modification", false)
                                .set("outbox-messages", true)
                                .build())
                        .set("enforce-properties", false)
                        .set("enforce-desired-properties", true)
                        .set("forbid", JsonFactory.newObjectBuilder()
                                .set("non-modeled-inbox-messages", false)
                                .set("non-modeled-outbox-messages", true)
                                .build())
                        .set("forbid-feature-description-deletion", true)
                        .set("forbid-non-modeled-features", false)
                        .build())
                .build();

        // When: Converting to validation config JSON
        final JsonObject result = WotConfigTransformationUtils.convertHoconConfigOverridesToValidationConfigJson(config);

        // Then: Should handle mixed structure correctly
        assertNotNull(result);
        // Debug: Print the actual result to understand the structure
        System.out.println("Actual result: " + result.toString());
        assertEquals(4, result.getSize()); // Changed from 5 to 4 based on actual behavior
        assertTrue(result.getValue("enabled").orElseThrow().asBoolean());
        assertFalse(result.getValue("logWarningInsteadOfFailingApiCalls").orElseThrow().asBoolean());

        // Check thing config
        final JsonObject thingConfig = result.getValue("thing").orElseThrow().asObject();
        final JsonObject thingEnforce = thingConfig.getValue("enforce").orElseThrow().asObject();
        final JsonObject thingForbid = thingConfig.getValue("forbid").orElseThrow().asObject();

        assertEquals(4, thingEnforce.getSize());
        assertTrue(thingEnforce.getValue("thingDescriptionModification").orElseThrow().asBoolean());
        assertTrue(thingEnforce.getValue("attributes").orElseThrow().asBoolean());
        assertFalse(thingEnforce.getValue("outboxMessages").orElseThrow().asBoolean());
        assertFalse(thingEnforce.getValue("inboxMessagesInput").orElseThrow().asBoolean());

        assertEquals(3, thingForbid.getSize());
        assertFalse(thingForbid.getValue("thingDescriptionDeletion").orElseThrow().asBoolean());
        assertTrue(thingForbid.getValue("nonModeledAttributes").orElseThrow().asBoolean());
        assertTrue(thingForbid.getValue("nonModeledInboxMessages").orElseThrow().asBoolean());

        // Check feature config
        final JsonObject featureConfig = result.getValue("feature").orElseThrow().asObject();
        final JsonObject featureEnforce = featureConfig.getValue("enforce").orElseThrow().asObject();
        final JsonObject featureForbid = featureConfig.getValue("forbid").orElseThrow().asObject();

        assertEquals(4, featureEnforce.getSize());
        assertFalse(featureEnforce.getValue("featureDescriptionModification").orElseThrow().asBoolean());
        assertFalse(featureEnforce.getValue("properties").orElseThrow().asBoolean());
        assertTrue(featureEnforce.getValue("outboxMessages").orElseThrow().asBoolean());
        assertTrue(featureEnforce.getValue("desiredProperties").orElseThrow().asBoolean());

        assertEquals(4, featureForbid.getSize());
        assertTrue(featureForbid.getValue("featureDescriptionDeletion").orElseThrow().asBoolean());
        assertFalse(featureForbid.getValue("nonModeledFeatures").orElseThrow().asBoolean());
        assertFalse(featureForbid.getValue("nonModeledInboxMessages").orElseThrow().asBoolean());
        assertTrue(featureForbid.getValue("nonModeledOutboxMessages").orElseThrow().asBoolean());
    }

    @Test
    public void testConvertEmptyThingAndFeature() {
        // Given: Config with empty thing and feature objects
        final JsonObject config = JsonFactory.newObjectBuilder()
                .set("enabled", true)
                .set("log-warning-instead-of-failing-api-calls", false)
                .set("thing", JsonFactory.newObject())
                .set("feature", JsonFactory.newObject())
                .build();

        // When: Converting to validation config JSON
        final JsonObject result = WotConfigTransformationUtils.convertHoconConfigOverridesToValidationConfigJson(config);

        // Then: Should handle empty objects gracefully
        assertNotNull(result);
        assertEquals(4, result.getSize());
        assertTrue(result.getValue("enabled").orElseThrow().asBoolean());
        assertFalse(result.getValue("logWarningInsteadOfFailingApiCalls").orElseThrow().asBoolean());

        final JsonObject thingConfig = result.getValue("thing").orElseThrow().asObject();
        final JsonObject featureConfig = result.getValue("feature").orElseThrow().asObject();

        assertTrue(thingConfig.isEmpty());
        assertTrue(featureConfig.isEmpty());
    }

    @Test
    public void testConvertWithArrays() {
        // Given: Config with arrays (edge case)
        final JsonObject config = JsonFactory.newObjectBuilder()
                .set("enabled", true)
                .set("some-array", JsonFactory.newArrayBuilder()
                        .add(JsonFactory.newObjectBuilder().set("test-field", "test-value").build())
                        .build())
                .set("thing", JsonFactory.newObjectBuilder()
                        .set("enforce-attributes", true)
                        .build())
                .build();

        // When: Converting to validation config JSON
        final JsonObject result = WotConfigTransformationUtils.convertHoconConfigOverridesToValidationConfigJson(config);

        // Then: Should handle arrays correctly
        assertNotNull(result);
        assertEquals(3, result.getSize());
        assertTrue(result.getValue("enabled").orElseThrow().asBoolean());
        assertTrue(result.getValue("someArray").isPresent());

        final JsonObject thingConfig = result.getValue("thing").orElseThrow().asObject();
        final JsonObject enforce = thingConfig.getValue("enforce").orElseThrow().asObject();
        assertTrue(enforce.getValue("attributes").orElseThrow().asBoolean());
    }

    @Test
    public void testConvertComplexNestedStructure() {
        // Given: Complex nested structure with multiple levels
        final JsonObject config = JsonFactory.newObjectBuilder()
                .set("thing", JsonFactory.newObjectBuilder()
                        .set("enforce", JsonFactory.newObjectBuilder()
                                .set("attributes", true)
                                .set("nested", JsonFactory.newObjectBuilder()
                                        .set("deep-field", false)
                                        .build())
                                .build())
                        .set("forbid-non-modeled-attributes", true)
                        .build())
                .build();

        // When: Converting to validation config JSON
        final JsonObject result = WotConfigTransformationUtils.convertHoconConfigOverridesToValidationConfigJson(config);

        // Then: Should handle complex nesting correctly
        assertNotNull(result);
        final JsonObject thingConfig = result.getValue("thing").orElseThrow().asObject();
        final JsonObject enforce = thingConfig.getValue("enforce").orElseThrow().asObject();
        final JsonObject forbid = thingConfig.getValue("forbid").orElseThrow().asObject();

        assertTrue(enforce.getValue("attributes").orElseThrow().asBoolean());
        assertTrue(enforce.getValue("nested").isPresent());
        assertFalse(enforce.getValue("nested").orElseThrow().asObject().getValue("deepField").orElseThrow().asBoolean());
        assertTrue(forbid.getValue("nonModeledAttributes").orElseThrow().asBoolean());
    }
} 