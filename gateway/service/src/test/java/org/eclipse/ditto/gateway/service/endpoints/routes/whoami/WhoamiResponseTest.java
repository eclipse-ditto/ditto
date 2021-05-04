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
package org.eclipse.ditto.gateway.service.endpoints.routes.whoami;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.api.common.CommonCommandResponse;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link WhoamiResponse}.
 */
public final class WhoamiResponseTest {

    private static final String DEFAULT_SUBJECT = "eclipse:subject";
    private static final String OTHER_SUBJECT = "other:sub";
    private static final AuthorizationContext AUTHORIZATION_CONTEXT =
            AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AuthorizationSubject.newInstance(DEFAULT_SUBJECT),
                    AuthorizationSubject.newInstance(OTHER_SUBJECT));

    private static final UserInformation USER_INFORMATION =
            DefaultUserInformation.fromAuthorizationContext(AUTHORIZATION_CONTEXT);
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .correlationId("any")
            .responseRequired(false)
            .build();
    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(CommonCommandResponse.JsonFields.TYPE, WhoamiResponse.TYPE)
            .set(CommonCommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
            .set("userInformation", USER_INFORMATION.toJson())
            .build();

    @Test
    public void of() {
        final WhoamiResponse response = WhoamiResponse.of(USER_INFORMATION, DITTO_HEADERS);
        assertThat(response.getDittoHeaders()).isEqualTo(DITTO_HEADERS);
        assertThat(response.getEntity()).isEqualTo(USER_INFORMATION.toJson());
    }

    @Test
    public void toJsonReturnsExpected() {
        final WhoamiResponse response = WhoamiResponse.of(USER_INFORMATION, DITTO_HEADERS);
        final JsonObject actualJson = response.toJson();

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final WhoamiResponse response = WhoamiResponse.fromJson(KNOWN_JSON, DITTO_HEADERS);

        assertThat(response.getDittoHeaders()).isEqualTo(DITTO_HEADERS);
        assertThat(response.getEntity()).isEqualTo(USER_INFORMATION.toJson());
    }

    @Test
    public void setEntity() {
        final UserInformation otherUserInformation = DefaultUserInformation.fromAuthorizationContext(
                AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance(OTHER_SUBJECT)));
        final JsonValue expectedEntity = otherUserInformation.toJson();
        final WhoamiResponse originalResponse = WhoamiResponse.of(USER_INFORMATION, DITTO_HEADERS);

        final WhoamiResponse responseWithNewEntity = originalResponse.setEntity(expectedEntity);
        final JsonValue newEntity = responseWithNewEntity.getEntity(JsonSchemaVersion.LATEST);

        assertThat(newEntity).isEqualTo(expectedEntity);
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(WhoamiResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(WhoamiResponse.class, areImmutable(), provided(JsonObject.class).isAlsoImmutable());
    }

}
