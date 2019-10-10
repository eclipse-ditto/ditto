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
package org.eclipse.ditto.services.connectivity.mapping.custom;

import static org.eclipse.ditto.services.connectivity.mapping.custom.ConnectionStatusMessageMapper.HEADER_HUB_CREATION_TIME;
import static org.eclipse.ditto.services.connectivity.mapping.custom.ConnectionStatusMessageMapper.HEADER_HUB_TTD;
import static org.eclipse.ditto.services.connectivity.mapping.custom.ConnectionStatusMessageMapper.MAPPING_OPTIONS_PROPERTIES_FEATURE_ID;
import static org.eclipse.ditto.services.connectivity.mapping.custom.ConnectionStatusMessageMapper.MAPPING_OPTIONS_PROPERTIES_THING_ID;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMessageMapperConfiguration;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperConfiguration;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Tests {@link ConnectionStatusMessageMapper}.
 */
public class ConnectionStatusMessageMapperTest {

    private static final String HEADER_HUB_DEVICE_ID = "device_id";
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
        validHeader.put(HEADER_HUB_DEVICE_ID, "headerNamespace:headerDeviceId");
        validHeader.put(HEADER_HUB_TTD, "12");
        validHeader.put(HEADER_HUB_CREATION_TIME, "1568816054");
        validMapperConfig = DefaultMessageMapperConfiguration.of("valid", validConfigProps);
    }

    @Test
    public void doForwardMapWithValidUseCase() {
        underTest.configure(mappingConfig, validMapperConfig);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isNotEmpty();
    }

    //Validate external message header
    @Test
    public void doForwardMapWithMissingHeaderTTD() {
        underTest.configure(mappingConfig, validMapperConfig);
        final Map<String, String> invalidHeader = validHeader;
        invalidHeader.remove(HEADER_HUB_TTD);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithMissingHeaderCreationTime() {
        underTest.configure(mappingConfig, validMapperConfig);
        final Map<String, String> invalidHeader = validHeader;
        invalidHeader.remove(HEADER_HUB_CREATION_TIME);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
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
        Assertions.assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithInvalidHeaderTTD() {
        underTest.configure(mappingConfig, validMapperConfig);
        final Map<String, String> invalidHeader = validHeader;
        invalidHeader.replace(HEADER_HUB_TTD, "Invalid Value");
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithInvalidHeaderCreationTime() {
        underTest.configure(mappingConfig, validMapperConfig);
        final Map<String, String> invalidHeader = validHeader;
        invalidHeader.replace(HEADER_HUB_CREATION_TIME, "Invalid Value");
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithInvalidTTDValue() {
        underTest.configure(mappingConfig, validMapperConfig);
        final Map<String, String> invalidHeader = validHeader;
        final String invalidTTDValue = "-5625";
        invalidHeader.replace(HEADER_HUB_TTD, invalidTTDValue);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
    }

    //Validate mapping context options
    @Test
    public void doForwardMappingContextWithoutFeatureId() {
        underTest.configure(mappingConfig, validMapperConfig);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult.get(0).getPayload().getPath().getFeatureId().get())
                .isEqualTo("ConnectionStatus");
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
        Assertions.assertThat(mappingResult.get(0).getPayload().getPath().getFeatureId().get())
                .isEqualTo(individualFeatureId);
    }

    @Test
    public void doForwardMappingContextWithoutThingId() {
        underTest.configure(mappingConfig, DefaultMessageMapperConfiguration.of("valid", Collections.emptyMap()));
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
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
        Assertions.assertThat(mappingResult.get(0).getTopicPath().getId())
                .isEqualTo(ThingId.of(validHeader.get(HEADER_HUB_DEVICE_ID)).getName());
    }

}
