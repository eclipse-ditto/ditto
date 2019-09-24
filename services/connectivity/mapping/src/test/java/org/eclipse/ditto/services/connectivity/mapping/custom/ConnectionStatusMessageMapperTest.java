package org.eclipse.ditto.services.connectivity.mapping.custom;

import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;

import org.assertj.core.api.Assertions;
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

    private static HashMap<String, String> validHeader;

    @Before
    public void setUp() {
        mappingConfig = DefaultMappingConfig.of(ConfigFactory.empty());
        underTest = new ConnectionStatusMessageMapper();
        underTest.configure(mappingConfig, DefaultMessageMapperConfiguration.of(Collections.emptyMap()));

        validHeader = new HashMap<>();
        validHeader.put(HEADER_HUB_DEVICE_ID, "namespace:deviceId");
        validHeader.put(HEADER_HUB_TTD, "-1");
        validHeader.put(HEADER_HUB_CREATION_TIME, "1568816054");
    }

    @Test
    public void doForwardMapWithValidUseCase() {
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(validHeader).build();
        final Optional<Adaptable> mappingResult = underTest.map(externalMessage);

        Assertions.assertThat(mappingResult).isNotEmpty();
    }

    @Test
    public void doForwardMapWithMissingHeaderTTD() {
        final HashMap<String, String> invalidHeader = validHeader;
        invalidHeader.remove(HEADER_HUB_TTD);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final Optional<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithMissingHeaderCreationTime() {
        final HashMap<String, String> invalidHeader = validHeader;
        invalidHeader.remove(HEADER_HUB_CREATION_TIME);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final Optional<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithMissingHeaderDeviceID() {
        final HashMap<String, String> invalidHeader = validHeader;
        invalidHeader.remove(HEADER_HUB_DEVICE_ID);
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final Optional<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithInvalidHeaderTTD() {
        final HashMap<String, String> invalidHeader = validHeader;
        invalidHeader.replace(HEADER_HUB_TTD, "Invalid Value");
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final Optional<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithInvalidHeaderCreationTime() {
        final HashMap<String, String> invalidHeader = validHeader;
        invalidHeader.replace(HEADER_HUB_CREATION_TIME, "Invalid Value");
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final Optional<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
    }

    @Test
    public void doForwardMapWithInvalidTTDValue() {
        final HashMap<String, String> invalidHeader = validHeader;
        invalidHeader.replace(HEADER_HUB_TTD, "-5625");
        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(invalidHeader).build();
        final Optional<Adaptable> mappingResult = underTest.map(externalMessage);
        Assertions.assertThat(mappingResult).isEmpty();
    }
}
