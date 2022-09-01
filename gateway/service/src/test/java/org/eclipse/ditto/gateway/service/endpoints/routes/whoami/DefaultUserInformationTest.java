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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultUserInformation}.
 */
public final class DefaultUserInformationTest {

    @Test
    public void fromAuthorizationContext() {
        final String defaultSubjectId = "google:i-am-user";
        final AuthorizationSubject defaultSubject = AuthorizationSubject.newInstance(defaultSubjectId);
        final String otherSubjectId = "eclipse:i-am-not-google";
        final AuthorizationSubject otherSubject = AuthorizationSubject.newInstance(otherSubjectId);

        final AuthorizationContext authorizationContext = AuthorizationContext.newInstance(
                DittoAuthorizationContextType.UNSPECIFIED, defaultSubject, otherSubject);

        final DefaultUserInformation userInformation = DefaultUserInformation.fromAuthorizationContext(authorizationContext);

        assertThat(userInformation.getDefaultSubject()).contains(defaultSubjectId);
        assertThat(userInformation.getSubjects()).containsExactly(defaultSubjectId, otherSubjectId);
    }

    @Test
    public void fromEmptyAuthorizationContext() {
        final AuthorizationContext authorizationContext = AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                Collections.emptyList());

        final DefaultUserInformation userInformation = DefaultUserInformation.fromAuthorizationContext(authorizationContext);

        assertThat(userInformation.getDefaultSubject()).isEmpty();
        assertThat(userInformation.getSubjects()).isEmpty();
    }

    @Test
    public void fromAuthorizationContextThrowsOnNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> DefaultUserInformation.fromAuthorizationContext(null));
    }

    @Test
    public void fromJson() {
        final String defaultSubjectId = "google:i-am-user";
        final String otherSubjectId = "eclipse:i-am-not-google";
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("defaultSubject", defaultSubjectId)
                .set("subjects", JsonArray.of(defaultSubjectId, otherSubjectId))
                .build();

        final DefaultUserInformation userInformation = DefaultUserInformation.fromJson(json);

        assertThat(userInformation.getDefaultSubject()).contains(defaultSubjectId);
        assertThat(userInformation.getSubjects()).containsExactly(defaultSubjectId, otherSubjectId);
    }

    @Test
    public void fromEmptyJson() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("defaultSubject", (String) null)
                .set("subjects", JsonArray.empty())
                .build();

        final DefaultUserInformation userInformation = DefaultUserInformation.fromJson(json);

        assertThat(userInformation.getDefaultSubject()).isEmpty();
        assertThat(userInformation.getSubjects()).isEmpty();
    }

    @Test
    public void fromJsonThrowsOnMissingSubjects() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("defaultSubject", (String) null)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> DefaultUserInformation.fromJson(json));
    }

    @Test
    public void fromJsonThrowsOnNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> DefaultUserInformation.fromJson(null));
    }

    @Test
    public void toJson() {
        final String defaultSubjectId = "google:i-am-user";
        final String otherSubjectId = "eclipse:i-am-not-google";
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("defaultSubject", defaultSubjectId)
                .set("subjects", JsonArray.of(defaultSubjectId, otherSubjectId))
                .build();

        final DefaultUserInformation userInformation = DefaultUserInformation.fromJson(json);

        assertThat(userInformation.toJson()).isEqualTo(json);
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(DefaultUserInformation.class)
                .verify();
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(DefaultUserInformation.class,
                areImmutable());
    }
}
