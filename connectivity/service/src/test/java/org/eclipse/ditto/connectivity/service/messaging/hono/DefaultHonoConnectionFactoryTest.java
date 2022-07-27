/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.hono;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.service.config.DefaultHonoConfig;
import org.eclipse.ditto.connectivity.service.config.HonoConfig;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.ConfigFactory;

@RunWith(MockitoJUnitRunner.class)
public class DefaultHonoConnectionFactoryTest {

    private Connection dummyConnection;
    private HonoConfig honoConfig;

    @Rule
    public final ActorSystemResource actorSystemResource =
            ActorSystemResource.newInstance(ConfigFactory.load("test"));

    private DefaultHonoConnectionFactory underTest;

    @Before
    public void setup() {
        honoConfig = new DefaultHonoConfig(actorSystemResource.getActorSystem());
        dummyConnection = ConnectivityModelFactory.connectionFromJson(getResource("test-connection.json"));
        underTest = DefaultHonoConnectionFactory.getInstance(actorSystemResource.getActorSystem(), dummyConnection);
    }

    @Test
    public void testGetCredentials() {
        final var EXPECTED_CREDENTIALS = honoConfig.getUserPasswordCredentials();
        assertEquals(EXPECTED_CREDENTIALS, underTest.getCredentials());
    }

    @Test
    public void testGetTenantId() {
        var EXPECTED_TENANT_ID = "";
        assertEquals(EXPECTED_TENANT_ID, underTest.getTenantId());
    }

    private static JsonObject getResource(final String fileName) {
        try (var resourceStream = DefaultHonoConnectionFactoryTest.class.getClassLoader().getResourceAsStream(fileName)) {
            assert resourceStream != null;
            return JsonObject.of(new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Test resource not found: " + fileName);
        }
    }

}