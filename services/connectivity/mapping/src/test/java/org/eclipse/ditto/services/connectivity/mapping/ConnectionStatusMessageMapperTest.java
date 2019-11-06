/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.connectivity.mapping.ConnectionStatusMessageMapper.DEFAULT_FEATURE_ID;
import static org.eclipse.ditto.services.connectivity.mapping.ConnectionStatusMessageMapper.FEATURE_PROPERTY_READY_SINCE;
import static org.eclipse.ditto.services.connectivity.mapping.ConnectionStatusMessageMapper.FEATURE_PROPERTY_READY_UNTIL;
import static org.eclipse.ditto.services.connectivity.mapping.ConnectionStatusMessageMapper.HEADER_HONO_CREATION_TIME;
import static org.eclipse.ditto.services.connectivity.mapping.ConnectionStatusMessageMapper.HEADER_HONO_TTD;
import static org.eclipse.ditto.services.connectivity.mapping.ConnectionStatusMessageMapper.MAPPING_OPTIONS_PROPERTIES_FEATURE_ID;
import static org.eclipse.ditto.services.connectivity.mapping.ConnectionStatusMessageMapper.MAPPING_OPTIONS_PROPERTIES_THING_ID;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.typesafe.config.ConfigFactory;

/**
 * Tests {@link org.eclipse.ditto.services.connectivity.mapping.ConnectionStatusMessageMapper}.
 */
public class ConnectionStatusMessageMapperTest {

    private static final String HEADER_HONO_DEVICE_ID = "device_id";
    private static final String CREATION_TIME_STR = "1571214120000";
    private static final Instant CREATION_TIME = Instant.ofEpochMilli(Long.parseLong(CREATION_TIME_STR));
    private static final String TTD_STR = "20";
    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final String EXPECTED_READY_UNTIL_IN_DISTANT_FUTURE = "9999-12-31T23:59:59Z";

    private static MappingConfig mappingConfig;

    private Map<String, String> validHeader;
    private Map<String, String> validConfigProps;
    private DefaultMessageMapperConfiguration validMapperConfig;
    private MessageMapper underTest;

    @Before
    public void setUp() {
        mappingConfig = DefaultMappingConfig.of(ConfigFactory.empty());
        underTest = new ConnectionStatusMessageMapper();

        validConfigProps = new HashMap<>();
        validConfigProps.put(ConnectionStatusMessageMapper.MAPPING_OPTIONS_PROPERTIES_THING_ID,
                "configNamespace:configDeviceId");

        validHeader = new HashMap<>();
        validHeader.put(HEADER_HONO_DEVICE_ID, "headerNamespace:headerDeviceId");
        validHeader.put(HEADER_HONO_TTD, TTD_STR);
        validHeader.put(HEADER_HONO_CREATION_TIME, CREATION_TIME_STR);
        validMapperConfig = DefaultMessageMapperConfiguration.of("valid", validConfigProps);
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void doForwardMapWithValidUseCase() {
        // GIVEN
        underTest.configure(mappingConfig, validMapperConfig);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();

        // WHEN
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        // THEN
        assertThat(mappingResult).isNotEmpty();
        final Adaptable adaptable = mappingResult.get(0);
        final Signal<?> signal = DittoProtocolAdapter.newInstance().fromAdaptable(adaptable);
        assertThat(signal).isInstanceOf(ModifyFeature.class);
        final ModifyFeature modifyFeature = (ModifyFeature) signal;
        assertThat(getDefinitionIdentifier(modifyFeature)).contains(ConnectionStatusMessageMapper.FEATURE_DEFINITION);
        assertThat(extractProperty(modifyFeature, FEATURE_PROPERTY_READY_SINCE)).contains(
                JsonValue.of(CREATION_TIME.toString()));
        assertThat(extractProperty(modifyFeature, FEATURE_PROPERTY_READY_UNTIL)).contains(
                JsonValue.of(CREATION_TIME.plusSeconds(Long.parseLong(TTD_STR)).toString()));
    }

    @Test
    public void doForwardMapWithValidUseCaseTtdZero() {
        // GIVEN
        underTest.configure(mappingConfig, validMapperConfig);
        validHeader.put(HEADER_HONO_TTD, "0");
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();

        // WHEN
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        // THEN
        assertThat(mappingResult).isNotEmpty();
        final Adaptable adaptable = mappingResult.get(0);
        final Signal<?> signal = DittoProtocolAdapter.newInstance().fromAdaptable(adaptable);
        assertThat(signal).isInstanceOf(ModifyFeatureProperty.class);
        final ModifyFeatureProperty modifyFeatureProperty = (ModifyFeatureProperty) signal;
        assertThat(modifyFeatureProperty.getPropertyValue())
                .isEqualTo(JsonValue.of(CREATION_TIME.toString()));
    }

    @Test
    public void doForwardMapWithValidUseCaseTtdMinusOne() {
        // GIVEN
        underTest.configure(mappingConfig, validMapperConfig);
        validHeader.put(HEADER_HONO_TTD, "-1");
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();

        // WHEN
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        // THEN
        final Adaptable adaptable = mappingResult.get(0);
        final Signal<?> signal = DITTO_PROTOCOL_ADAPTER.fromAdaptable(adaptable);
        assertThat(signal).isInstanceOf(ModifyFeature.class);
        final ModifyFeature modifyFeature = (ModifyFeature) signal;
        assertThat(getDefinitionIdentifier(modifyFeature)).contains(ConnectionStatusMessageMapper.FEATURE_DEFINITION);
        assertThat(extractProperty(modifyFeature, FEATURE_PROPERTY_READY_SINCE)).contains(
                JsonValue.of(CREATION_TIME.toString()));
        assertThat(extractProperty(modifyFeature, FEATURE_PROPERTY_READY_UNTIL)).contains(
                JsonValue.of(EXPECTED_READY_UNTIL_IN_DISTANT_FUTURE));
    }

    //Validate external message header
    @Test
    public void doForwardMapWithMissingHeaderTTD() {
        // GIVEN
        underTest.configure(mappingConfig, validMapperConfig);
        final Map<String, String> invalidHeader = validHeader;
        invalidHeader.remove(HEADER_HONO_TTD);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        // WHEN
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        // THEN
        assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithMissingHeaderCreationTime() {
        underTest.configure(mappingConfig, validMapperConfig);
        final Map<String, String> invalidHeader = validHeader;
        invalidHeader.remove(HEADER_HONO_CREATION_TIME);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithMissingHeader() {
        final Map<String, String> props = new HashMap<>();
        props.put(MAPPING_OPTIONS_PROPERTIES_THING_ID, "{{ header:thing-id }}"); // header does not exist
        final MessageMapperConfiguration thingIdWithPlaceholder
                = DefaultMessageMapperConfiguration.of("placeholder", props);
        underTest.configure(mappingConfig, thingIdWithPlaceholder);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithInvalidHeaderTTD() {
        underTest.configure(mappingConfig, validMapperConfig);
        final Map<String, String> invalidHeader = validHeader;
        invalidHeader.replace(HEADER_HONO_TTD, "Invalid Value");
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithInvalidHeaderCreationTime() {
        underTest.configure(mappingConfig, validMapperConfig);
        final Map<String, String> invalidHeader = validHeader;
        invalidHeader.replace(HEADER_HONO_CREATION_TIME, "Invalid Value");
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithInvalidTTDValue() {
        underTest.configure(mappingConfig, validMapperConfig);
        final Map<String, String> invalidHeader = validHeader;
        final String invalidTTDValue = "-5625";
        invalidHeader.replace(HEADER_HONO_TTD, invalidTTDValue);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult).isEmpty();
    }

    //Validate mapping context options
    @Test
    public void doForwardMappingContextWithoutFeatureId() {
        underTest.configure(mappingConfig, validMapperConfig);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult.get(0).getPayload().getPath().getFeatureId().get())
                .isEqualTo(DEFAULT_FEATURE_ID);
    }

    @Test
    public void doForwardMappingContextWithIndividualFeatureId() {
        final String individualFeatureId = "individualFeatureId";
        final Map<String, String> props = new HashMap<>(validConfigProps);
        props.put(MAPPING_OPTIONS_PROPERTIES_FEATURE_ID, individualFeatureId);
        final MessageMapperConfiguration individualFeatureIdConfig
                = DefaultMessageMapperConfiguration.of("placeholder", props);
        underTest.configure(mappingConfig, individualFeatureIdConfig);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult.get(0).getPayload().getPath().getFeatureId().get())
                .isEqualTo(individualFeatureId);
    }

    @Test
    public void doForwardMappingContextWithoutThingId() {
        exception.expect(MessageMapperConfigurationInvalidException.class);
        underTest.configure(mappingConfig, DefaultMessageMapperConfiguration.of("valid", Collections.emptyMap()));
    }

    @Test
    public void doForwardMappingContextWithWrongThingId() {
        exception.expect(DittoRuntimeException.class);
        final Map<String, String> props = new HashMap<>();
        props.put(MAPPING_OPTIONS_PROPERTIES_THING_ID, "Invalid Value");
        final MessageMapperConfiguration wrongThingId
                = DefaultMessageMapperConfiguration.of("invalidThingId", props);
        underTest.configure(mappingConfig, wrongThingId);
    }

    @Test
    public void doForwardMappingContextWithThingIdMapping() {
        final Map<String, String> props = new HashMap<>();
        props.put(MAPPING_OPTIONS_PROPERTIES_THING_ID, "{{ header:device_id }}");
        final MessageMapperConfiguration thingIdWithPlaceholder
                = DefaultMessageMapperConfiguration.of("placeholder", props);
        underTest.configure(mappingConfig, thingIdWithPlaceholder);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult.get(0).getTopicPath().getId())
                .isEqualTo(ThingId.of(validHeader.get(HEADER_HONO_DEVICE_ID)).getName());
    }

    @Test
    public void doForwardMappingContextWithWrongThingIdMapping() {
        final Map<String, String> props = new HashMap<>();
        props.put(MAPPING_OPTIONS_PROPERTIES_THING_ID, "{{ header:device_id }}");
        final MessageMapperConfiguration thingIdWithPlaceholder
                = DefaultMessageMapperConfiguration.of("placeholder", props);
        underTest.configure(mappingConfig, thingIdWithPlaceholder);
        final Map<String, String> invalidHeader = validHeader;
        invalidHeader.replace(HEADER_HONO_DEVICE_ID, "Invalid Value");
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult).isEmpty();
    }

    private Optional<JsonValue> extractProperty(final ModifyFeature modifyFeature,
            final String featureProperty) {
        return modifyFeature.getFeature()
                .getProperty(ConnectionStatusMessageMapper.FEATURE_PROPERTY_CATEGORY_STATUS + "/" +
                        featureProperty);
    }

    private Optional<String> getDefinitionIdentifier(final ModifyFeature modifyFeature) {
        return modifyFeature.getFeature()
                .getDefinition()
                .map(FeatureDefinition::getFirstIdentifier)
                .map(FeatureDefinition.Identifier::toString);
    }
}
