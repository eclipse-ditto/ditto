/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.wot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.eclipse.ditto.base.api.common.CommonCommandResponse;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.wot.model.ThingDescription;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveWotDiscoveryThingDirectoryResponse}.
 */
public final class RetrieveWotDiscoveryThingDirectoryResponseTest {

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .correlationId("test-correlation-id")
            .responseRequired(false)
            .build();

    private static final JsonObject THING_DESCRIPTION_JSON = JsonFactory.newObjectBuilder()
            .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
            .set("@type", "ThingDirectory")
            .set("id", "urn:ditto:wot:thing-directory")
            .set("title", "Thing Description Directory (TDD) of Eclipse Ditto")
            .build();

    private static final ThingDescription THING_DESCRIPTION =
            ThingDescription.fromJson(THING_DESCRIPTION_JSON);

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveWotDiscoveryThingDirectoryResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void ofWithNullThingDescriptionThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> RetrieveWotDiscoveryThingDirectoryResponse.of(null, DITTO_HEADERS));
    }

    @Test
    public void ofWithNullHeadersThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> RetrieveWotDiscoveryThingDirectoryResponse.of(THING_DESCRIPTION, null));
    }

    @Test
    public void ofCreatesExpectedResponse() {
        final RetrieveWotDiscoveryThingDirectoryResponse response =
                RetrieveWotDiscoveryThingDirectoryResponse.of(THING_DESCRIPTION, DITTO_HEADERS);

        assertThat(response.getDittoHeaders()).isEqualTo(DITTO_HEADERS);
        assertThat(response.getHttpStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getEntity(JsonSchemaVersion.V_2)).isEqualTo(THING_DESCRIPTION.toJson());
    }

    @Test
    public void toJsonContainsExpectedFields() {
        final RetrieveWotDiscoveryThingDirectoryResponse response =
                RetrieveWotDiscoveryThingDirectoryResponse.of(THING_DESCRIPTION, DITTO_HEADERS);
        final JsonObject actualJson = response.toJson(JsonSchemaVersion.V_2, field -> true);

        assertThat(actualJson.getValue(CommonCommandResponse.JsonFields.TYPE))
                .contains(RetrieveWotDiscoveryThingDirectoryResponse.TYPE);
        assertThat(actualJson.getValue(CommonCommandResponse.JsonFields.STATUS))
                .contains(HttpStatus.OK.getCode());
        assertThat(actualJson.getValue("thingDescription"))
                .contains(THING_DESCRIPTION_JSON);
    }

    @Test
    public void fromJsonToJsonRoundTrip() {
        final JsonObject knownJson = JsonFactory.newObjectBuilder()
                .set(CommonCommandResponse.JsonFields.TYPE, RetrieveWotDiscoveryThingDirectoryResponse.TYPE)
                .set(CommonCommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
                .set("thingDescription", THING_DESCRIPTION_JSON)
                .build();

        final RetrieveWotDiscoveryThingDirectoryResponse response =
                RetrieveWotDiscoveryThingDirectoryResponse.fromJson(knownJson, DITTO_HEADERS);

        assertThat(response.getDittoHeaders()).isEqualTo(DITTO_HEADERS);
        assertThat(response.getEntity(JsonSchemaVersion.V_2)).isEqualTo(THING_DESCRIPTION.toJson());
    }

    @Test
    public void setEntity() {
        final JsonObject otherThingDescriptionJson = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("id", "urn:other:directory")
                .set("title", "Other Directory")
                .build();
        final ThingDescription otherThingDescription = ThingDescription.fromJson(otherThingDescriptionJson);

        final RetrieveWotDiscoveryThingDirectoryResponse originalResponse =
                RetrieveWotDiscoveryThingDirectoryResponse.of(THING_DESCRIPTION, DITTO_HEADERS);

        final RetrieveWotDiscoveryThingDirectoryResponse responseWithNewEntity =
                originalResponse.setEntity(otherThingDescriptionJson);

        assertThat(responseWithNewEntity.getEntity(JsonSchemaVersion.V_2))
                .isEqualTo(otherThingDescription.toJson());
    }

    @Test
    public void setDittoHeadersReturnsNewInstance() {
        final RetrieveWotDiscoveryThingDirectoryResponse original =
                RetrieveWotDiscoveryThingDirectoryResponse.of(THING_DESCRIPTION, DITTO_HEADERS);
        final DittoHeaders newHeaders = DittoHeaders.newBuilder()
                .correlationId("new-correlation-id")
                .build();

        final RetrieveWotDiscoveryThingDirectoryResponse withNewHeaders = original.setDittoHeaders(newHeaders);

        assertThat(withNewHeaders).isNotSameAs(original);
        assertThat(withNewHeaders.getDittoHeaders().getCorrelationId())
                .contains("new-correlation-id");
    }

    @Test
    public void toStringContainsExpectedInformation() {
        final RetrieveWotDiscoveryThingDirectoryResponse response =
                RetrieveWotDiscoveryThingDirectoryResponse.of(THING_DESCRIPTION, DITTO_HEADERS);

        assertThat(response.toString())
                .contains(RetrieveWotDiscoveryThingDirectoryResponse.class.getSimpleName())
                .contains("thingDescription");
    }

}
