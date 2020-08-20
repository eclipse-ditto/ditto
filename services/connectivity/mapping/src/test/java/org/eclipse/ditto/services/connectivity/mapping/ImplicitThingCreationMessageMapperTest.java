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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.junit.Before;

import com.typesafe.config.ConfigFactory;

public class ImplicitThingCreationMessageMapperTest {

    private static final String HEADER_HONO_DEVICE_ID = "device_id";
    private static final String OPTIONAL_HEADER_HONO_ENTITY_ID = "entity_id";

    private static final String THING_TEMPLATE_PLACEHOLDERS = "{" +
            "thingId: {{ header:device_id }}," +
            "policyId: {{ header:entity_id }}" +
            "}";

    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

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
        validConfigProps.put(ImplicitThingCreationMessageMapper.THING_TEMPLATE,
                THING_TEMPLATE_PLACEHOLDERS);

        validHeader = new HashMap<>();
        validHeader.put(HEADER_HONO_DEVICE_ID, "headerNamespace:headerDeviceId");
        validHeader.put(OPTIONAL_HEADER_HONO_ENTITY_ID, "headerNamespace:headerEntityId");

        validMapperConfig = DefaultMessageMapperConfiguration.of("valid", validConfigProps);
    }


}
