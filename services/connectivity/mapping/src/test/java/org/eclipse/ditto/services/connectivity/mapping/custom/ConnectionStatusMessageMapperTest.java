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

import java.util.HashMap;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMessageMapperConfiguration;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

public class ConnectionStatusMessageMapperTest {

    private MessageMapper underTest;
    private static MappingConfig mappingConfig;

    public static final String HEADER_HUB_DEVICE_ID = "device_id";
    public static final String HEADER_HUB_TTD = "ttd";
    public static final String HEADER_HUB_CREATION_TIME = "creation-time";

    public static final String MAPPING_OPTIONS_PROPERTIES_THINGID = "thingId";
    public static final String MAPPING_OPTIONS_PROPERTIES_FEATUREID = "featureId";

    private static HashMap<String, String> validHeader;
    private static HashMap<String, String> validConfigProps;

    @Before
    public void setUp() {
        mappingConfig = DefaultMappingConfig.of(ConfigFactory.empty());
        underTest = new ConnectionStatusMessageMapper();

        validConfigProps = new HashMap<>();
        validConfigProps.put(MAPPING_OPTIONS_PROPERTIES_THINGID, "configNamespace:configDeviceId");

        validHeader = new HashMap<>();
        validHeader.put(HEADER_HUB_DEVICE_ID, "headerNamespace:headerDeviceId");
        validHeader.put(HEADER_HUB_TTD, "12");
        validHeader.put(HEADER_HUB_CREATION_TIME, "1568816054");
    }

    @Test
    public void doForwardMapWithValidUseCase() {
        underTest.configure(mappingConfig, DefaultMessageMapperConfiguration.of(validConfigProps));
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        Assertions.assertThat(mappingResult).isNotEmpty();
    }

    //Validate external message header
    @Test
    public void doForwardMapWithMissingHeaderTTD() {
        underTest.configure(mappingConfig, DefaultMessageMapperConfiguration.of(validConfigProps));
        final HashMap<String, String> invalidHeader = validHeader;
        invalidHeader.remove(HEADER_HUB_TTD);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithMissingHeaderCreationTime() {
        underTest.configure(mappingConfig, DefaultMessageMapperConfiguration.of(validConfigProps));
        final HashMap<String, String> invalidHeader = validHeader;
        invalidHeader.remove(HEADER_HUB_CREATION_TIME);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithMissingHeaderDeviceID() {
        underTest.configure(mappingConfig, DefaultMessageMapperConfiguration.of(validConfigProps));
        final HashMap<String, String> invalidHeader = validHeader;
        invalidHeader.remove(HEADER_HUB_DEVICE_ID);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithInvalidHeaderTTD() {
        underTest.configure(mappingConfig, DefaultMessageMapperConfiguration.of(validConfigProps));
        final HashMap<String, String> invalidHeader = validHeader;
        invalidHeader.replace(HEADER_HUB_TTD, "Invalid Value");
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithInvalidHeaderCreationTime() {
        underTest.configure(mappingConfig, DefaultMessageMapperConfiguration.of(validConfigProps));
        final HashMap<String, String> invalidHeader = validHeader;
        invalidHeader.replace(HEADER_HUB_CREATION_TIME, "Invalid Value");
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithInvalidTTDValue() {
        underTest.configure(mappingConfig, DefaultMessageMapperConfiguration.of(validConfigProps));
        final HashMap<String, String> invalidHeader = validHeader;
        final String invalidTTDValue = "-5625";
        invalidHeader.replace(HEADER_HUB_TTD, invalidTTDValue);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
    }

    //Validate mapping context options
    @Test
    public void doForwardMappingContextWithoutFeatureId() {
        underTest.configure(mappingConfig, DefaultMessageMapperConfiguration.of(validConfigProps));
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult.get(0).getPayload().getPath().getFeatureId().get())
                .isEqualTo("ConnectionStatus");
    }

    @Test
    public void doForwardMappingContextWithIndividualFeatureId() {
        final String individualFeatureId = "individualFeatureId";
        validConfigProps.put(MAPPING_OPTIONS_PROPERTIES_FEATUREID, individualFeatureId);
        underTest.configure(mappingConfig, DefaultMessageMapperConfiguration.of(validConfigProps));
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult.get(0).getPayload().getPath().getFeatureId().get())
                .isEqualTo(individualFeatureId);
    }

    @Test
    public void doForwardMappingContextWithoutThingId() {
        validConfigProps.remove(MAPPING_OPTIONS_PROPERTIES_THINGID);
        underTest.configure(mappingConfig, DefaultMessageMapperConfiguration.of(validConfigProps));
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        Assertions.assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMappingContextWithThingIdMapping() {
        validConfigProps.replace(MAPPING_OPTIONS_PROPERTIES_THINGID, "{{ header:device_id }}");
        underTest.configure(mappingConfig, DefaultMessageMapperConfiguration.of(validConfigProps));
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult.get(0).getTopicPath().getId())
                .isEqualTo(ThingId.of(validHeader.get(HEADER_HUB_DEVICE_ID)).getName());
    }
}
