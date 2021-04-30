/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.security.authentication.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link DittoJwtAuthorizationSubjectsProvider}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DittoJwtAuthorizationSubjectsProviderTest {

    @Test
    public void verifyThatTheDefaultJwtSubjectPlaceholderWorks() {
        final String subjectIssuer = "testIssuer";
        final String tokenSubject = "testSubject";

        final JsonWebToken jsonWebToken = createToken("{\"sub\": \"" + tokenSubject + "\"}");
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer);

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken);

        assertThat(authSubjects.size()).isEqualTo(1);
        assertThat(authSubjects.get(0)).isEqualTo(AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenSubject));
    }

    @Test
    public void verifyThatASingleJwtSubjectPlaceholderWorks() {
        final String subjectIssuer = "testIssuer";
        final String tokenAudience = "some-audience";

        final JsonWebToken jsonWebToken = createToken("{\"aud\": \"" + tokenAudience + "\"}");
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer, List.of("{{ jwt:aud }}"));

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken);

        assertThat(authSubjects.size()).isEqualTo(1);
        assertThat(authSubjects.get(0)).isEqualTo(AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenAudience));
    }

    @Test
    public void verifyThatASingleJwtSubjectPlaceholderWorksWithJsonArray() {
        final String subjectIssuer = "testIssuer";
        final String tokenAudience1 = "some-audience";
        final String tokenAudience2 = "other-audience";

        final JsonWebToken jsonWebToken = createToken(
                "{\"aud\": [\"" + tokenAudience1 + "\", \"" + tokenAudience2 + "\"]}");
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer,
                List.of("{{ jwt:aud }}"));

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken);

        assertThat(authSubjects).containsExactlyInAnyOrder(
            AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenAudience1),
            AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenAudience2)
        );
    }

    @Test
    public void verifyThatMultipleJwtSubjectPlaceholdersWork() {
        final String subjectIssuer = "testIssuer";
        final String tokenAudience1 = "some-audience";
        final String tokenAudience2 = "other-audience";
        final String tokenGroup = "any-group";

        final JsonWebToken jsonWebToken = createToken(
                "{\"aud\": [\"" + tokenAudience1 + "\", \"" + tokenAudience2 + "\"],\"grp\": \"" + tokenGroup + "\"}");

        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer,
                List.of("{{ jwt:aud }}", "{{ jwt:grp }}"));

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken);

        assertThat(authSubjects).containsExactlyInAnyOrder(
            AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenAudience1),
            AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenAudience2),
            AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenGroup)
        );
    }

    @Test
    public void verifyThatUnresolvablePlaceholdersAreDiscarded() {
        final String subjectIssuer = "testIssuer";
        final String tokenGroup = "any-group";

        final JsonWebToken jsonWebToken = createToken(
                "{\"grp\": \"" + tokenGroup + "\"}");

        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer,
                List.of("{{ jwt:aud }}"));

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken);

        assertThat(authSubjects.size()).isEqualTo(0);
    }

    JsonWebToken createToken(final String body) {
        final JsonWebToken jsonWebToken = mock(JsonWebToken.class);
        when(jsonWebToken.getIssuer()).thenReturn(JwtTestConstants.ISSUER);
        when(jsonWebToken.getBody()).thenReturn(JsonObject.of(body));
        return jsonWebToken;
    }

    JwtSubjectIssuersConfig createSubjectIssuersConfig(final String subjectIssuer, final List<String> subjectTemplates) {
        final JwtSubjectIssuerConfig subjectIssuerConfig = new JwtSubjectIssuerConfig(
            SubjectIssuer.newInstance(subjectIssuer),
            JwtTestConstants.ISSUER,
            subjectTemplates);
        return JwtSubjectIssuersConfig.fromJwtSubjectIssuerConfigs(List.of(subjectIssuerConfig));
    }

    JwtSubjectIssuersConfig createSubjectIssuersConfig(final String subjectIssuer) {
        final JwtSubjectIssuerConfig subjectIssuerConfig = new JwtSubjectIssuerConfig(
            SubjectIssuer.newInstance(subjectIssuer),
            JwtTestConstants.ISSUER);
        return JwtSubjectIssuersConfig.fromJwtSubjectIssuerConfigs(List.of(subjectIssuerConfig));
    }

}
