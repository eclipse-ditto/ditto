/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

public class ImplicitThingCreationMessageMapperTest {

    private static final String HEADER_HONO_DEVICE_ID = "device_id";
    private static final String HEADER_HONO_GATEWAY_ID = "gateway_id";
    private static final String OPTIONAL_HEADER_HONO_ENTITY_ID = "entity_id";

    private static final String THING_TEMPLATE = "{" +
            "\"thingId\": \"{{ header:device_id }}\"," +
            "\"policyId\": \"{{ header:entity_id }}\"," +
            "\"attributes\": {" +
            "\"info\": {" +
            "\"gatewayId\": \"{{ header:gateway_id }}\"" +
            "}" +
            "}" +
            "}";

    private static final String THING_TEMPLATE_WITHOUT_PLACEHOLDERS = "{" +
            "\"thingId\": \"some:validThingId!\"," +
            "\"policyId\": \"some:validPolicyId!\"," +
            "\"attributes\": {" +
            "\"info\": {" +
            "\"gatewayId\": \"some:validGatewayId!\"" +
            "}" +
            "}" +
            "}";

    private static MappingConfig mappingConfig;
    private MessageMapper underTest;

    @Before
    public void setUp() {
        mappingConfig = DefaultMappingConfig.of(ConfigFactory.empty());
        underTest = new ImplicitThingCreationMessageMapper();
    }

    //Validate mapping context options
    @Test
    public void doForwardMappingContextWithSubstitutedPlaceholders() {

        final Map<String, String> headers = createValidHeaders();

        underTest.configure(mappingConfig, createMapperConfig(null));

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers).build();

        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        final Thing expectedThing =
                createExpectedThing("headerNamespace:headerDeviceId", "headerNamespace:headerEntityId",
                        "headerNamespace:headerGatewayId");

        assertThat(mappingResult.get(0).getPayload().getValue()).isPresent();

        final Thing mappedThing = getMappedThing(mappingResult);

        assertThat(mappedThing.getEntityId())
                .isEqualTo(expectedThing.getEntityId());

        assertThat(mappedThing.getPolicyEntityId())
                .isEqualTo(expectedThing.getPolicyEntityId());

        assertThat(mappedThing.getAttributes())
                .isEqualTo(expectedThing.getAttributes());

    }

    @Test
    public void throwErrorIfMappingConfigIsMissing() {

        final DefaultMessageMapperConfiguration invalidMapperConfig = createMapperConfig("{}");

        assertThatExceptionOfType(MessageMapperConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.configure(mappingConfig, invalidMapperConfig));
    }

    @Test
    public void throwErrorIfThingIdIsMissingInConfig() {

        final String thingMissing = "{" +
                "\"policyId\": \"{{ header:entity_id }}\"" +
                "}";

        final DefaultMessageMapperConfiguration invalidMapperConfig = createMapperConfig(thingMissing);

        assertThatExceptionOfType(MessageMapperConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.configure(mappingConfig, invalidMapperConfig));
    }

    @Test
    public void throwErrorIfHeaderForPlaceholderIsMissing() {
        underTest.configure(mappingConfig, createMapperConfig(null));

        final Map<String, String> missingEntityHeader = new HashMap<>();
        missingEntityHeader.put(HEADER_HONO_DEVICE_ID, "headerNamespace:headerDeviceId");

        final ExternalMessage externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(missingEntityHeader).build();

        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> underTest.map(externalMessage));
    }

    @Test
    public void doForwardEvenWithoutAnyPlaceholders() {
        final Map<String, String> headers = createValidHeaders();

        underTest.configure(mappingConfig, createMapperConfig(THING_TEMPLATE_WITHOUT_PLACEHOLDERS));

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers).build();

        final List<Adaptable> mappingResult = underTest.map(externalMessage);

        final Thing expectedThing =
                createExpectedThing("some:validThingId!", "some:validPolicyId!", "some:validGatewayId!");

        final Thing mappedThing = getMappedThing(mappingResult);

        assertThat(mappedThing.getEntityId())
                .isEqualTo(expectedThing.getEntityId());

        assertThat(mappedThing.getPolicyEntityId())
                .isEqualTo(expectedThing.getPolicyEntityId());
    }

    private static Thing getMappedThing(final List<Adaptable> mappingResult) {
        return mappingResult.stream().findFirst()
                .map(Adaptable::getPayload)
                .flatMap(Payload::getValue)
                .map(JsonValue::asObject)
                .map(ThingsModelFactory::newThing)
                .orElseGet(() -> fail("Mapping Result did not contain a Thing."));
    }

    private Map<String, String> createValidHeaders() {
        final Map<String, String> validHeader = new HashMap<>();
        validHeader.put(HEADER_HONO_DEVICE_ID, "headerNamespace:headerDeviceId");
        validHeader.put(HEADER_HONO_GATEWAY_ID, "headerNamespace:headerGatewayId");
        validHeader.put(OPTIONAL_HEADER_HONO_ENTITY_ID, "headerNamespace:headerEntityId");
        return validHeader;
    }

    private Thing createExpectedThing(final String thingId, final String policyId, final String gatewayId) {
        return ThingsModelFactory.newThing("{" +
                "\"thingId\": \"" + thingId + "\"," +
                "\"policyId\": \"" + policyId + "\"," +
                "\"attributes\": {" +
                "\"info\": {" +
                "\"gatewayId\": \"" + gatewayId + "\"" +
                "}" +
                "}" +
                "}");
    }

    private DefaultMessageMapperConfiguration createMapperConfig(@Nullable String customTemplate) {
        final Map<String, String> configPropsWithoutPolicyId = new HashMap<>();

        configPropsWithoutPolicyId.put("thing", customTemplate != null ? customTemplate : THING_TEMPLATE);

        return DefaultMessageMapperConfiguration.of("valid", configPropsWithoutPolicyId);
    }

}
