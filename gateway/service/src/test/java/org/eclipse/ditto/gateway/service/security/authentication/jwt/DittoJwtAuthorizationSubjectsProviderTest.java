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
import java.util.UUID;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import akka.actor.ActorSystem;

/**
 * Unit test for {@link DittoJwtAuthorizationSubjectsProvider}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DittoJwtAuthorizationSubjectsProviderTest {

    private ActorSystem actorSystem;

    @Before
    public void setup() {
        actorSystem = ActorSystem.create(UUID.randomUUID().toString());
    }

    @Test
    public void verifyThatTheDefaultJwtSubjectPlaceholderWorks() {
        final String subjectIssuer = "testIssuer";
        final String tokenSubject = "testSubject";

        final JsonWebToken jsonWebToken = createToken("{\"sub\": \"" + tokenSubject + "\"}");
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer);

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken);

        assertThat(authSubjects).hasSize(1);
        assertThat(authSubjects.get(0)).isEqualTo(AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenSubject));
    }

    @Test
    public void verifyThatASingleJwtSubjectPlaceholderWorks() {
        final String subjectIssuer = "testIssuer";
        final String tokenAudience = "some-audience";

        final JsonWebToken jsonWebToken = createToken("{\"aud\": \"" + tokenAudience + "\"}");
        final JwtSubjectIssuersConfig subjectIssuersConfig =
                createSubjectIssuersConfig(subjectIssuer, List.of("test-{{ jwt:aud }}"));

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken);

        assertThat(authSubjects).hasSize(1);
        assertThat(authSubjects.get(0)).isEqualTo(
                AuthorizationSubject.newInstance(subjectIssuer + ":test-" + tokenAudience));
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
                .of(actorSystem, subjectIssuersConfig);

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
                List.of("{{ jwt:aud }}", "{{ jwt:grp | fn:lower()}}"));

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken);

        assertThat(authSubjects).containsExactlyInAnyOrder(
                AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenAudience1),
                AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenAudience2),
                AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenGroup)
        );
    }

    @Test
    public void verifyThatFilteringOnJwtArrayClaimsWork() {
        final String subjectIssuer = "testIssuer";
        final String tokenAudience1 = "some-audience";
        final String tokenAudience2 = "other-audience";
        final String tokenAudience3 = "noone-audience";

        final JsonWebToken jsonWebToken = createToken(
                "{\"aud\": [\"" + tokenAudience1 + "\", \"" + tokenAudience2 + "\", \"" + tokenAudience3 + "\"]}");

        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer,
                List.of("{{ jwt:aud | fn:filter('like','some*|noone*') }}"));

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken);

        assertThat(authSubjects).containsExactlyInAnyOrder(
                AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenAudience1),
                AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenAudience3)
        );
    }

    @Test
    public void verifyThatFilteringOnJwtArrayClaimsContainingSplitWork() {
        final String subjectIssuer = "testIssuer";
        final String tokenAudience1 = "some-audience";
        final String tokenAudience2 = "veni,vidi,vici";
        final String tokenAudience3 = "vendetta-audience";

        final JsonWebToken jsonWebToken = createToken(
                "{\"aud\": [\"" + tokenAudience1 + "\", \"" + tokenAudience2 + "\", \"" + tokenAudience3 + "\"]}");

        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer,
                List.of("{{ jwt:aud | fn:split(',') | fn:filter('like','v*') | fn:substring-after('v') | fn:replace('-audience','') }}"));

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken);

        assertThat(authSubjects).containsExactlyInAnyOrder(
                AuthorizationSubject.newInstance(subjectIssuer + ":eni"),
                AuthorizationSubject.newInstance(subjectIssuer + ":idi"),
                AuthorizationSubject.newInstance(subjectIssuer + ":ici"),
                AuthorizationSubject.newInstance(subjectIssuer + ":endetta")
        );
    }

    @Test
    public void verifyThatUnresolvablePlaceholdersAreDiscarded() {
        final String subjectIssuer = "testIssuer";
        final String tokenGroup = "any-group";

        final JsonWebToken jsonWebToken = createToken(
                "{\"grp\": \"" + tokenGroup + "\"}");

        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer,
                List.of("{{ jwt:aud | fn:lower() }}"));

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken);

        assertThat(authSubjects).isEmpty();
    }

    @Test
    public void assertSplitPipelineFunctionWorks() {
        final String subjectIssuer = "testIssuer";
        final String scope = "openid all profile nothing";

        final JsonWebToken jsonWebToken = createToken(
                "{\"scope\": \"" + scope + "\"}");

        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer,
                List.of("{{ jwt:scope | fn:split(\" \") }}"));

        // jwt:scope -> "openid all profile nothing"
        // fn:split(" ") -> ["openid", "all", "profile", "nothing"]

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken);

        assertThat(authSubjects).containsExactly(
                AuthorizationSubject.newInstance(subjectIssuer + ":" + "openid"),
                AuthorizationSubject.newInstance(subjectIssuer + ":" + "all"),
                AuthorizationSubject.newInstance(subjectIssuer + ":" + "profile"),
                AuthorizationSubject.newInstance(subjectIssuer + ":" + "nothing")
        );
    }

    @Test
    public void assertComplexFunctionWorks() {
        final String subjectIssuer = "testIssuer";
        final String scope = "[\"openid:test hello profile nothing\", \"only rest\", \"relax,a\"]";

        final JsonWebToken jsonWebToken = createToken(
                "{\"scope\": " + scope + "}");

        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer,
                List.of("{{ jwt:scope | fn:split(\" \") | fn:filter('ne', 'hello') | fn:filter('like', '*o*|*x*') }}"));

        // jwt:scope -> ["openid:test hello profile nothing", "only rest", "relax,a"]
        // fn:split(" ") -> ["openid:test", "hello", "profile", "nothing", "only", "rest", "relax,a"]
        // fn:filter('ne', 'hello') -> ["openid:test", "profile", "nothing", "only", "rest", "relax,a"]
        // fn:filter('like', '*o*|*x*') -> ["openid:test", "profile", "nothing", "only", "relax,a"]

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken);

        assertThat(authSubjects).containsExactly(
                AuthorizationSubject.newInstance(subjectIssuer + ":" + "openid:test"),
                AuthorizationSubject.newInstance(subjectIssuer + ":" + "profile"),
                AuthorizationSubject.newInstance(subjectIssuer + ":" + "nothing"),
                AuthorizationSubject.newInstance(subjectIssuer + ":" + "only"),
                AuthorizationSubject.newInstance(subjectIssuer + ":" + "relax,a")
        );
    }

    @Test
    public void assertComplexFunctionWithTextWorks() {
        final String subjectIssuer = "testIssuer";
        final String scope = "ope,nid all profile nothing";
        final String tokenAudience1 = "some-audience";
        final String tokenAudience2 = "other:audience";

        final JsonWebToken jsonWebToken = createToken(
                "{\"aud\": [\"" + tokenAudience1 + "\", \"" + tokenAudience2 + "\"],\"scope\": \"" + scope + "\"}");

        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer,
                List.of("rest-{{ jwt:aud  | fn:split(\":\") }}-{{ jwt:scope | fn:split(\" \") | fn:split(\",\") | fn:filter('like', 'ope|all') }}-test"));

        // jwt:aud -> ["some-audience", "other:audience"]
        // fn:split(":") -> ["some-audience", "other", "audience"]

        // jwt:scope -> "ope,nid all profile nothing"
        // fn:split(" ") -> ["ope,nid", "all", "profile", "nothing"]
        // fn:split(",") -> ["ope", "nid", "all", "profile", "nothing"]
        // fn:filter('like', 'ope|all') -> ["ope", "all"]

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken);

        assertThat(authSubjects).containsExactly(
                AuthorizationSubject.newInstance(subjectIssuer + ":" + "rest-some-audience-ope-test"),
                AuthorizationSubject.newInstance(subjectIssuer + ":" + "rest-some-audience-all-test"),
                AuthorizationSubject.newInstance(subjectIssuer + ":" + "rest-other-ope-test"),
                AuthorizationSubject.newInstance(subjectIssuer + ":" + "rest-other-all-test"),
                AuthorizationSubject.newInstance(subjectIssuer + ":" + "rest-audience-ope-test"),
                AuthorizationSubject.newInstance(subjectIssuer + ":" + "rest-audience-all-test")
        );
    }

    private static JsonWebToken createToken(final String body) {
        final JsonWebToken jsonWebToken = mock(JsonWebToken.class);
        when(jsonWebToken.getIssuer()).thenReturn(JwtTestConstants.ISSUER);
        when(jsonWebToken.getBody()).thenReturn(JsonObject.of(body));
        return jsonWebToken;
    }


    private static JwtSubjectIssuersConfig createSubjectIssuersConfig(final String subjectIssuer,
            final List<String> subjectTemplates) {
        final JwtSubjectIssuerConfig subjectIssuerConfig = new JwtSubjectIssuerConfig(
                SubjectIssuer.newInstance(subjectIssuer),
                List.of(JwtTestConstants.ISSUER),
                subjectTemplates);
        return JwtSubjectIssuersConfig.fromJwtSubjectIssuerConfigs(List.of(subjectIssuerConfig));
    }

    private static JwtSubjectIssuersConfig createSubjectIssuersConfig(final String subjectIssuer) {
        final JwtSubjectIssuerConfig subjectIssuerConfig = new JwtSubjectIssuerConfig(
                SubjectIssuer.newInstance(subjectIssuer),
                List.of(JwtTestConstants.ISSUER));
        return JwtSubjectIssuersConfig.fromJwtSubjectIssuerConfigs(List.of(subjectIssuerConfig));
    }

}
