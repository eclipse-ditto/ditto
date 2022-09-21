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
package org.eclipse.ditto.connectivity.service.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.DefinitionIdentifier;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Tests {@link ConnectionStatusMessageMapper}.
 */
public class ConnectionStatusMessageMapperTest {

    private static final String HEADER_HONO_DEVICE_ID = "device_id";
    private static final String CREATION_TIME_STR = "1571214120000";
    private static final Instant CREATION_TIME = Instant.ofEpochMilli(Long.parseLong(CREATION_TIME_STR));
    private static final String TTD_STR = "20";
    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final String EXPECTED_READY_UNTIL_IN_DISTANT_FUTURE = "9999-12-31T23:59:59Z";

    private static Connection connection;
    private static ConnectivityConfig connectivityConfig;
    private static ActorSystem actorSystem;

    private Map<String, String> validHeader;
    private Map<String, JsonValue> validConfigProps;
    private Map<String, String> validConditions;
    private DefaultMessageMapperConfiguration validMapperConfig;
    private MessageMapper underTest;

    @Before
    public void setUp() {
        connection = TestConstants.createConnection();
        connectivityConfig = TestConstants.CONNECTIVITY_CONFIG;
        actorSystem = ActorSystem.create("Test", TestConstants.CONFIG);
        underTest = new ConnectionStatusMessageMapper(actorSystem, Mockito.mock(Config.class));

        validConfigProps = Map.of(ConnectionStatusMessageMapper.MAPPING_OPTIONS_PROPERTIES_THING_ID,
                JsonValue.of("configNamespace:configDeviceId"));
        validConditions = Map.of();

        validHeader = new HashMap<>();
        validHeader.put(HEADER_HONO_DEVICE_ID, "headerNamespace:headerDeviceId");
        validHeader.put(ConnectionStatusMessageMapper.HEADER_HONO_TTD, TTD_STR);
        validHeader.put(ConnectionStatusMessageMapper.HEADER_HONO_CREATION_TIME, CREATION_TIME_STR);
        validMapperConfig =
                DefaultMessageMapperConfiguration.of("valid", validConfigProps, validConditions, validConditions);
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            actorSystem.terminate();
        }
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void doForwardMapWithValidUseCase() {
        // GIVEN
        underTest.configure(connection, connectivityConfig, validMapperConfig, actorSystem);
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
        assertThat(extractProperty(modifyFeature, ConnectionStatusMessageMapper.FEATURE_PROPERTY_READY_SINCE)).contains(
                JsonValue.of(CREATION_TIME.toString()));
        assertThat(extractProperty(modifyFeature, ConnectionStatusMessageMapper.FEATURE_PROPERTY_READY_UNTIL)).contains(
                JsonValue.of(CREATION_TIME.plusSeconds(Long.parseLong(TTD_STR)).toString()));
    }

    @Test
    public void emittedCommandShouldExplicitlyRequestNoAcknowledgements() {
        // GIVEN
        underTest.configure(connection, connectivityConfig, validMapperConfig, actorSystem);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();

        // WHEN
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        // THEN
        assertThat(mappingResult).isNotEmpty();
        final Adaptable adaptable = mappingResult.get(0);
        final Signal<?> signal = DittoProtocolAdapter.newInstance().fromAdaptable(adaptable);
        assertThat(signal).isInstanceOf(ModifyFeature.class);
        assertThat(signal.getDittoHeaders()).containsKey(DittoHeaderDefinition.REQUESTED_ACKS.getKey());
        assertThat(signal.getDittoHeaders().getAcknowledgementRequests()).isEmpty();
    }

    @Test
    public void doForwardMapWithValidUseCaseTtdZero() {
        // GIVEN
        underTest.configure(connection, connectivityConfig, validMapperConfig, actorSystem);
        validHeader.put(ConnectionStatusMessageMapper.HEADER_HONO_TTD, "0");
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
        underTest.configure(connection, connectivityConfig, validMapperConfig, actorSystem);
        validHeader.put(ConnectionStatusMessageMapper.HEADER_HONO_TTD, "-1");
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();

        // WHEN
        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        // THEN
        final Adaptable adaptable = mappingResult.get(0);
        final Signal<?> signal = DITTO_PROTOCOL_ADAPTER.fromAdaptable(adaptable);
        assertThat(signal).isInstanceOf(ModifyFeature.class);
        final ModifyFeature modifyFeature = (ModifyFeature) signal;
        assertThat(getDefinitionIdentifier(modifyFeature)).contains(ConnectionStatusMessageMapper.FEATURE_DEFINITION);
        assertThat(extractProperty(modifyFeature, ConnectionStatusMessageMapper.FEATURE_PROPERTY_READY_SINCE)).contains(
                JsonValue.of(CREATION_TIME.toString()));
        assertThat(extractProperty(modifyFeature, ConnectionStatusMessageMapper.FEATURE_PROPERTY_READY_UNTIL)).contains(
                JsonValue.of(EXPECTED_READY_UNTIL_IN_DISTANT_FUTURE));
    }

    //Validate external message header
    @Test
    public void doForwardMapWithMissingHeaderTTD() {
        // GIVEN
        underTest.configure(connection, connectivityConfig, validMapperConfig, actorSystem);
        final Map<String, String> invalidHeader = validHeader;
        invalidHeader.remove(ConnectionStatusMessageMapper.HEADER_HONO_TTD);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        // WHEN
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        // THEN
        assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithMissingHeaderCreationTime() {
        underTest.configure(connection, connectivityConfig, validMapperConfig, actorSystem);
        final Map<String, String> invalidHeader = validHeader;
        invalidHeader.remove(ConnectionStatusMessageMapper.HEADER_HONO_CREATION_TIME);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithMissingHeader() {
        final Map<String, JsonValue> props =
                Map.of(ConnectionStatusMessageMapper.MAPPING_OPTIONS_PROPERTIES_THING_ID,
                        JsonValue.of("{{ header:thing-id }}"));
        final MessageMapperConfiguration thingIdWithPlaceholder
                = DefaultMessageMapperConfiguration.of("placeholder", props, validConditions, validConditions);
        underTest.configure(connection, connectivityConfig, thingIdWithPlaceholder, actorSystem);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithInvalidHeaderTTD() {
        underTest.configure(connection, connectivityConfig, validMapperConfig, actorSystem);
        final Map<String, String> invalidHeader = validHeader;
        invalidHeader.replace(ConnectionStatusMessageMapper.HEADER_HONO_TTD, "Invalid Value");
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithInvalidHeaderCreationTime() {
        underTest.configure(connection, connectivityConfig, validMapperConfig, actorSystem);
        final Map<String, String> invalidHeader = validHeader;
        invalidHeader.replace(ConnectionStatusMessageMapper.HEADER_HONO_CREATION_TIME, "Invalid Value");
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithInvalidTTDValue() {
        underTest.configure(connection, connectivityConfig, validMapperConfig, actorSystem);
        final Map<String, String> invalidHeader = validHeader;
        final String invalidTTDValue = "-5625";
        invalidHeader.replace(ConnectionStatusMessageMapper.HEADER_HONO_TTD, invalidTTDValue);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult).isEmpty();
    }

    //Validate mapping context options
    @Test
    public void doForwardMappingContextWithoutFeatureId() {
        underTest.configure(connection, connectivityConfig, validMapperConfig, actorSystem);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult.get(0).getPayload().getPath().getFeatureId())
                .contains(ConnectionStatusMessageMapper.DEFAULT_FEATURE_ID);
    }

    @Test
    public void doForwardMappingContextWithIndividualFeatureId() {
        final String individualFeatureId = "individualFeatureId";
        final Map<String, JsonValue> props = new HashMap<>(validConfigProps);
        props.put(ConnectionStatusMessageMapper.MAPPING_OPTIONS_PROPERTIES_FEATURE_ID,
                JsonValue.of(individualFeatureId));
        final MessageMapperConfiguration individualFeatureIdConfig
                = DefaultMessageMapperConfiguration.of("placeholder", props, validConditions, validConditions);
        underTest.configure(connection, connectivityConfig, individualFeatureIdConfig, actorSystem);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult.get(0).getPayload().getPath().getFeatureId())
                .contains(individualFeatureId);
    }

    @Test
    public void doForwardMappingContextWithoutThingId() {
        exception.expect(MessageMapperConfigurationInvalidException.class);
        underTest.configure(connection, connectivityConfig,
                DefaultMessageMapperConfiguration.of("valid", Collections.emptyMap(), validConditions,
                        validConditions),
                actorSystem);
    }

    @Test
    public void doForwardMappingContextWithWrongThingId() {
        exception.expect(DittoRuntimeException.class);
        final Map<String, JsonValue> props =
                Map.of(ConnectionStatusMessageMapper.MAPPING_OPTIONS_PROPERTIES_THING_ID,
                        JsonValue.of("Invalid Value"));
        final MessageMapperConfiguration wrongThingId
                = DefaultMessageMapperConfiguration.of("invalidThingId", props, validConditions, validConditions);
        underTest.configure(connection, connectivityConfig, wrongThingId, actorSystem);
    }

    @Test
    public void doForwardMappingContextWithThingIdMapping() {
        final Map<String, JsonValue> props =
                Map.of(ConnectionStatusMessageMapper.MAPPING_OPTIONS_PROPERTIES_THING_ID,
                        JsonValue.of("{{ header:device_id }}"));
        final MessageMapperConfiguration thingIdWithPlaceholder
                = DefaultMessageMapperConfiguration.of("placeholder", props, validConditions, validConditions);
        underTest.configure(connection, connectivityConfig, thingIdWithPlaceholder, actorSystem);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final List<Adaptable> mappingResult = underTest.map(externalMessage);
        assertThat(mappingResult.get(0).getTopicPath().getEntityName())
                .isEqualTo(ThingId.of(validHeader.get(HEADER_HONO_DEVICE_ID)).getName());
    }

    @Test
    public void doForwardMappingContextWithWrongThingIdMapping() {
        final Map<String, JsonValue> props =
                Map.of(ConnectionStatusMessageMapper.MAPPING_OPTIONS_PROPERTIES_THING_ID,
                        JsonValue.of("{{ header:device_id }}"));
        final MessageMapperConfiguration thingIdWithPlaceholder
                = DefaultMessageMapperConfiguration.of("placeholder", props, validConditions, validConditions);
        underTest.configure(connection, connectivityConfig, thingIdWithPlaceholder, actorSystem);
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
                .map(DefinitionIdentifier::toString);
    }
}
