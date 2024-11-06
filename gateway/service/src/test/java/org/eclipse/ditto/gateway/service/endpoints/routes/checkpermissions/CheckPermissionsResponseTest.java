/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions;


import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.junit.Test;
import nl.jqno.equalsverifier.EqualsVerifier;

public final class CheckPermissionsResponseTest {

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .correlationId("test-correlation-id")
            .responseRequired(false)
            .build();

    private static final Map<String, Boolean> PERMISSION_RESULTS = Map.of("check1", true, "check2", false);
    private static final JsonObject RESULTS_JSON = JsonObject.newBuilder().set("check1", true).set("check2", false).build();

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set("type", CheckPermissionsResponse.TYPE)
            .set("status", HttpStatus.OK.getCode())
            .set("permissionResults", RESULTS_JSON)
            .build();
    private static CheckPermissionsResponse KNOWN_RESPONSE =  CheckPermissionsResponse.of(PERMISSION_RESULTS, DITTO_HEADERS);
    @Test
    public void of() {
        final CheckPermissionsResponse response = CheckPermissionsResponse.of(PERMISSION_RESULTS, DITTO_HEADERS);
        assertEquals(DITTO_HEADERS, response.getDittoHeaders());
        assertThat(response.getEntity()).isEqualTo(RESULTS_JSON);
    }

    @Test
    public void toJsonReturnsExpected() {
        final CheckPermissionsResponse response = CheckPermissionsResponse.of(PERMISSION_RESULTS, DITTO_HEADERS);
        final JsonObject actualJson = response.toJson();
        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final CheckPermissionsResponse response = CheckPermissionsResponse.fromJson(KNOWN_RESPONSE.toJson(), DITTO_HEADERS);
        assertEquals(DITTO_HEADERS, response.getDittoHeaders());
        assertThat(response.getEntity()).isEqualTo(KNOWN_RESPONSE.getEntity().asObject());
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(CheckPermissionsResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }
}
