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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.api.GatewayJwtPrerequisiteConditionNotMetException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

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

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

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

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

        assertThat(authSubjects).hasSize(1);
        assertThat(authSubjects.get(0)).isEqualTo(
                AuthorizationSubject.newInstance(subjectIssuer + ":test-" + tokenAudience));
    }

    @Test
    public void verifyThatInjectedClaimHeadersWithSimplePlaceholderWorks() {
        final String subjectIssuer = "testIssuer";
        final String tokenAudience = "some-audience";
        final String email = "ditto@eclipse.org";

        final JsonWebToken jsonWebToken = createToken("{" +
                "\"aud\": \"" + tokenAudience + "\", " +
                "\"email\": \"" + email + "\"" +
                "}");
        final JwtSubjectIssuersConfig subjectIssuersConfig =
                createSubjectIssuersConfig(subjectIssuer, List.of("test-{{ jwt:aud }}"),
                        Map.of("user-email", "{{ jwt:email }}")
                );

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final Map<String, String> injectedFromClaims = underTest.getAdditionalHeadersInjectedFromClaims(jsonWebToken);

        assertThat(injectedFromClaims).hasSize(1);
        assertThat(injectedFromClaims.get("user-email")).isEqualTo(email);
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

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

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

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

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

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

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

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

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

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

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

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

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

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

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

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

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
                subjectTemplates,
                Map.of()
        );
        return JwtSubjectIssuersConfig.fromJwtSubjectIssuerConfigs(List.of(subjectIssuerConfig));
    }

    private static JwtSubjectIssuersConfig createSubjectIssuersConfig(final String subjectIssuer,
            final List<String> subjectTemplates, final Map<String, String> injectClaimsIntoHeaders) {
        final JwtSubjectIssuerConfig subjectIssuerConfig = new JwtSubjectIssuerConfig(
                SubjectIssuer.newInstance(subjectIssuer),
                List.of(JwtTestConstants.ISSUER),
                subjectTemplates,
                injectClaimsIntoHeaders
        );
        return JwtSubjectIssuersConfig.fromJwtSubjectIssuerConfigs(List.of(subjectIssuerConfig));
    }

    private static JwtSubjectIssuersConfig createSubjectIssuersConfig(final String subjectIssuer) {
        final JwtSubjectIssuerConfig subjectIssuerConfig = new JwtSubjectIssuerConfig(
                SubjectIssuer.newInstance(subjectIssuer),
                List.of(JwtTestConstants.ISSUER));
        return JwtSubjectIssuersConfig.fromJwtSubjectIssuerConfigs(List.of(subjectIssuerConfig));
    }

    private static JwtSubjectIssuersConfig createSubjectIssuersConfigWithPrerequisiteConditions(
            final String subjectIssuer,
            final List<String> subjectTemplates,
            final List<String> prerequisiteConditions) {
        final JwtSubjectIssuerConfig subjectIssuerConfig = new JwtSubjectIssuerConfig(
                SubjectIssuer.newInstance(subjectIssuer),
                List.of(JwtTestConstants.ISSUER),
                subjectTemplates,
                Map.of(),
                prerequisiteConditions
        );
        return JwtSubjectIssuersConfig.fromJwtSubjectIssuerConfigs(List.of(subjectIssuerConfig));
    }

    @Test
    public void verifyThatPrerequisiteConditionPassesWhenConditionIsMet() {
        final String subjectIssuer = "testIssuer";
        final String tokenSubject = "testSubject";
        final String tokenAudience = "expected-audience";

        final JsonWebToken jsonWebToken = createToken(
                "{\"sub\": \"" + tokenSubject + "\", \"aud\": \"" + tokenAudience + "\"}");
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfigWithPrerequisiteConditions(
                subjectIssuer,
                List.of("{{ jwt:sub }}"),
                List.of("{{ jwt:aud | fn:filter('eq','expected-audience') }}")
        );

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

        assertThat(authSubjects).hasSize(1);
        assertThat(authSubjects.getFirst()).isEqualTo(AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenSubject));
    }

    @Test
    public void verifyThatPrerequisiteConditionFailsWhenConditionIsNotMet() {
        final String subjectIssuer = "testIssuer";
        final String tokenSubject = "testSubject";
        final String tokenAudience = "wrong-audience";

        final JsonWebToken jsonWebToken = createToken(
                "{\"sub\": \"" + tokenSubject + "\", \"aud\": \"" + tokenAudience + "\"}");
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfigWithPrerequisiteConditions(
                subjectIssuer,
                List.of("{{ jwt:sub }}"),
                List.of("{{ jwt:aud | fn:filter('eq','expected-audience') }}")
        );

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        assertThatThrownBy(() -> underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty()))
                .isInstanceOf(GatewayJwtPrerequisiteConditionNotMetException.class)
                .hasMessageContaining("did not meet a configured prerequisite condition");
    }

    @Test
    public void verifyThatPrerequisiteConditionFailsWhenClaimIsMissing() {
        final String subjectIssuer = "testIssuer";
        final String tokenSubject = "testSubject";

        final JsonWebToken jsonWebToken = createToken("{\"sub\": \"" + tokenSubject + "\"}");
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfigWithPrerequisiteConditions(
                subjectIssuer,
                List.of("{{ jwt:sub }}"),
                List.of("{{ jwt:aud }}")
        );

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        assertThatThrownBy(() -> underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty()))
                .isInstanceOf(GatewayJwtPrerequisiteConditionNotMetException.class)
                .hasMessageContaining("did not meet a configured prerequisite condition");
    }

    @Test
    public void verifyThatAllPrerequisiteConditionsMustPass() {
        final String subjectIssuer = "testIssuer";
        final String tokenSubject = "testSubject";
        final String tokenAudience = "expected-audience";
        final String tokenTenant = "wrong-tenant";

        final JsonWebToken jsonWebToken = createToken(
                "{\"sub\": \"" + tokenSubject + "\", \"aud\": \"" + tokenAudience + "\", \"tenant\": \"" + tokenTenant + "\"}");
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfigWithPrerequisiteConditions(
                subjectIssuer,
                List.of("{{ jwt:sub }}"),
                List.of(
                        "{{ jwt:aud | fn:filter('eq','expected-audience') }}",
                        "{{ jwt:tenant | fn:filter('like','Acme*') }}"
                )
        );

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        // Second condition fails because tenant doesn't start with "Acme"
        assertThatThrownBy(() -> underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty()))
                .isInstanceOf(GatewayJwtPrerequisiteConditionNotMetException.class)
                .hasMessageContaining("did not meet a configured prerequisite condition");
    }

    @Test
    public void verifyThatMultiplePrerequisiteConditionsAllPassingWorks() {
        final String subjectIssuer = "testIssuer";
        final String tokenSubject = "testSubject";
        final String tokenAudience = "expected-audience";
        final String tokenTenant = "AcmeCorp";

        final JsonWebToken jsonWebToken = createToken(
                "{\"sub\": \"" + tokenSubject + "\", \"aud\": \"" + tokenAudience + "\", \"tenant\": \"" + tokenTenant + "\"}");
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfigWithPrerequisiteConditions(
                subjectIssuer,
                List.of("{{ jwt:sub }}"),
                List.of(
                        "{{ jwt:aud | fn:filter('eq','expected-audience') }}",
                        "{{ jwt:tenant | fn:filter('like','Acme*') }}"
                )
        );

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

        assertThat(authSubjects).hasSize(1);
        assertThat(authSubjects.getFirst()).isEqualTo(AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenSubject));
    }

    @Test
    public void verifyThatEmptyPrerequisiteConditionsAllowsAllTokens() {
        final String subjectIssuer = "testIssuer";
        final String tokenSubject = "testSubject";

        final JsonWebToken jsonWebToken = createToken("{\"sub\": \"" + tokenSubject + "\"}");
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfigWithPrerequisiteConditions(
                subjectIssuer,
                List.of("{{ jwt:sub }}"),
                List.of()
        );

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

        assertThat(authSubjects).hasSize(1);
        assertThat(authSubjects.getFirst()).isEqualTo(AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenSubject));
    }

    @Test
    public void verifyThatHierarchyEncodedSubjectFromSingleAuthzEntryWorks() {
        final String subjectIssuer = "idp";

        // JWT with a single authz entry representing an SP-level user
        final JsonWebToken jsonWebToken = createToken("""
                {
                    "authz": [
                        {
                            "reseller": "io.test",
                            "service_provider": "acme",
                            "customer": "",
                            "roles": ["prod:things:operator"]
                        }
                    ]
                }
                """);
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer,
                List.of("{{ jwt:authz/reseller }}#{{ jwt:authz/service_provider }}#{{ jwt:authz/customer }}#{{ jwt:authz/roles }}"));

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

        // With a single authz entry, the hierarchy-encoded subject is correctly produced
        assertThat(authSubjects).containsExactly(
                AuthorizationSubject.newInstance("idp:io.test#acme##prod:things:operator")
        );
    }

    @Test
    public void verifyThatMultipleAuthzEntriesProduceCartesianProductOfSubjects() {
        final String subjectIssuer = "idp";

        // JWT with TWO authz entries: one SP-level, one reseller-level
        // This simulates a user having roles at multiple hierarchy levels
        final JsonWebToken jsonWebToken = createToken("""
                {
                    "authz": [
                        {
                            "reseller": "io.test",
                            "service_provider": "acme",
                            "customer": "",
                            "roles": ["op"]
                        },
                        {
                            "reseller": "io.test",
                            "service_provider": "",
                            "customer": "",
                            "roles": ["resellerAdmin"]
                        }
                    ]
                }
                """);
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer,
                List.of("{{ jwt:authz/reseller }}#{{ jwt:authz/service_provider }}#{{ jwt:authz/customer }}#{{ jwt:authz/roles }}"));

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

        // IMPORTANT: Each placeholder resolves independently across all array elements,
        // then a Cartesian product is formed. This produces cross-combinations:
        //   authz/reseller          → ["io.test"]  (deduplicated)
        //   authz/service_provider  → ["acme", ""]
        //   authz/customer          → [""]  (deduplicated)
        //   authz/roles             → ["op", "resellerAdmin"]
        // Cartesian product: 1 × 2 × 1 × 2 = 4 subjects
        // Two of these are WRONG cross-combinations (acme+resellerAdmin, ""(no-sp)+op)
        assertThat(authSubjects).containsExactlyInAnyOrder(
                AuthorizationSubject.newInstance("idp:io.test#acme##op"),           // ✓ correct SP-level
                AuthorizationSubject.newInstance("idp:io.test#acme##resellerAdmin"), // ✗ wrong mix
                AuthorizationSubject.newInstance("idp:io.test###op"),                // ✗ wrong mix
                AuthorizationSubject.newInstance("idp:io.test###resellerAdmin")      // ✓ correct reseller-level
        );
    }

    @Test
    public void verifyThatFilterOnNestedFieldInArrayOfObjectsWorks() {
        final String subjectIssuer = "idp";

        // JWT with authz entries for two different resellers
        final JsonWebToken jsonWebToken = createToken("""
                {
                    "authz": [
                        {
                            "reseller": "io.test",
                            "service_provider": "acme",
                            "roles": ["op"]
                        },
                        {
                            "reseller": "de.other",
                            "service_provider": "techpartner",
                            "roles": ["admin"]
                        }
                    ]
                }
                """);
        // Use fn:filter on a nested path within the array of objects
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer,
                List.of("{{ jwt:authz/reseller | fn:filter('eq','io.test') }}"));

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

        // The placeholder resolves authz/reseller to ["io.test", "de.other"],
        // then fn:filter('eq','io.test') keeps only "io.test"
        assertThat(authSubjects).containsExactly(
                AuthorizationSubject.newInstance("idp:io.test")
        );
    }

    @Test
    public void verifyThatPrerequisiteConditionWorksWithNestedFieldInArrayOfObjects() {
        final String subjectIssuer = "idp";

        // JWT with authz entries — the prerequisite checks for a specific reseller
        final JsonWebToken jsonWebToken = createToken("""
                {
                    "sub": "testUser",
                    "authz": [
                        {
                            "reseller": "io.test",
                            "service_provider": "acme",
                            "roles": ["op"]
                        },
                        {
                            "reseller": "de.other",
                            "service_provider": "techpartner",
                            "roles": ["admin"]
                        }
                    ]
                }
                """);
        // Prerequisite: authz must contain a reseller matching "io.test"
        // This mirrors namespace-access condition evaluation
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfigWithPrerequisiteConditions(
                subjectIssuer,
                List.of("{{ jwt:sub }}"),
                List.of("{{ jwt:authz/reseller | fn:filter('eq','io.test') }}")
        );

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

        // Prerequisite passes because authz/reseller resolves to ["io.test", "de.other"]
        // and filter('eq','io.test') produces non-empty result
        assertThat(authSubjects).containsExactly(
                AuthorizationSubject.newInstance("idp:testUser")
        );
    }

    @Test
    public void verifyThatPrerequisiteConditionFailsWhenNestedFieldFilterMatchesNothing() {
        final String subjectIssuer = "idp";

        // JWT with authz entries — no entry matches the required reseller
        final JsonWebToken jsonWebToken = createToken("""
                {
                    "sub": "testUser",
                    "authz": [
                        {
                            "reseller": "de.other",
                            "roles": ["admin"]
                        }
                    ]
                }
                """);
        // Prerequisite: authz must contain a reseller matching "io.test"
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfigWithPrerequisiteConditions(
                subjectIssuer,
                List.of("{{ jwt:sub }}"),
                List.of("{{ jwt:authz/reseller | fn:filter('eq','io.test') }}")
        );

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        // Prerequisite fails because no authz entry has reseller "io.test"
        assertThatThrownBy(() -> underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty()))
                .isInstanceOf(GatewayJwtPrerequisiteConditionNotMetException.class);
    }

    @Test
    public void verifyThatFormatFunctionAvoidsCrossProductForMultipleAuthzEntries() {
        final String subjectIssuer = "idp";

        // Same JWT as verifyThatMultipleAuthzEntriesProduceCartesianProductOfSubjects:
        // TWO authz entries, one SP-level, one reseller-level
        final JsonWebToken jsonWebToken = createToken("""
                {
                    "authz": [
                        {
                            "reseller": "io.test",
                            "service_provider": "acme",
                            "customer": "",
                            "roles": ["op"]
                        },
                        {
                            "reseller": "io.test",
                            "service_provider": "",
                            "customer": "",
                            "roles": ["resellerAdmin"]
                        }
                    ]
                }
                """);
        // Using fn:format() keeps field extractions correlated within each JSON object,
        // avoiding the incorrect Cartesian product across objects
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer,
                List.of("{{ jwt:authz | fn:format('{reseller}#{service_provider}#{customer}#{roles}') }}"));

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

        // Only 2 correct subjects — no cross-combinations
        assertThat(authSubjects).containsExactlyInAnyOrder(
                AuthorizationSubject.newInstance("idp:io.test#acme##op"),
                AuthorizationSubject.newInstance("idp:io.test###resellerAdmin")
        );
    }

    @Test
    public void verifyThatFormatFunctionWithMultipleRolesExpandsCorrectly() {
        final String subjectIssuer = "idp";

        // JWT with authz entries where one entry has multiple roles
        final JsonWebToken jsonWebToken = createToken("""
                {
                    "authz": [
                        {
                            "reseller": "io.test",
                            "service_provider": "acme",
                            "roles": ["op", "admin"]
                        },
                        {
                            "reseller": "de.other",
                            "service_provider": "techpartner",
                            "roles": ["viewer"]
                        }
                    ]
                }
                """);
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer,
                List.of("{{ jwt:authz | fn:format('{reseller}#{service_provider}#{roles}') }}"));

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

        // Array fields within a single object produce correct Cartesian product:
        // Entry 1: io.test#acme × [op, admin] = 2 subjects
        // Entry 2: de.other#techpartner × [viewer] = 1 subject
        assertThat(authSubjects).containsExactlyInAnyOrder(
                AuthorizationSubject.newInstance("idp:io.test#acme#op"),
                AuthorizationSubject.newInstance("idp:io.test#acme#admin"),
                AuthorizationSubject.newInstance("idp:de.other#techpartner#viewer")
        );
    }

    @Test
    public void verifyThatFormatFunctionWithSectionSyntaxHandlesNestedPermissions() {
        final String subjectIssuer = "idp";

        // JWT with authz entries containing nested permissions with actions arrays
        final JsonWebToken jsonWebToken = createToken("""
                {
                    "authz": [
                        {
                            "reseller": "io.test",
                            "service_provider": "acme",
                            "permissions": [
                                {"resource": "things", "actions": ["read", "write"]},
                                {"resource": "policies", "actions": ["read"]}
                            ]
                        }
                    ]
                }
                """);
        // Using section syntax {#permissions}...{/permissions} and {#actions}{.}{/actions}
        // to iterate over nested arrays of objects
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfig(subjectIssuer,
                List.of("{{ jwt:authz | fn:format('{reseller}:{service_provider}:{#permissions}{resource}:{#actions}{.}{/actions}{/permissions}') }}"));

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

        assertThat(authSubjects).containsExactlyInAnyOrder(
                AuthorizationSubject.newInstance("idp:io.test:acme:things:read"),
                AuthorizationSubject.newInstance("idp:io.test:acme:things:write"),
                AuthorizationSubject.newInstance("idp:io.test:acme:policies:read")
        );
    }

    @Test
    public void verifyThatPrerequisiteConditionWorksWithArrayClaimMatchingOneValue() {
        final String subjectIssuer = "testIssuer";
        final String tokenSubject = "testSubject";

        final JsonWebToken jsonWebToken = createToken(
                "{\"sub\": \"" + tokenSubject + "\", \"aud\": [\"other-aud\", \"expected-audience\", \"another-aud\"]}");
        final JwtSubjectIssuersConfig subjectIssuersConfig = createSubjectIssuersConfigWithPrerequisiteConditions(
                subjectIssuer,
                List.of("{{ jwt:sub }}"),
                List.of("{{ jwt:aud | fn:filter('eq','expected-audience') }}")
        );

        final DittoJwtAuthorizationSubjectsProvider underTest = DittoJwtAuthorizationSubjectsProvider
                .of(actorSystem, subjectIssuersConfig);

        final List<AuthorizationSubject> authSubjects = underTest.getAuthorizationSubjects(jsonWebToken, DittoHeaders.empty());

        assertThat(authSubjects).hasSize(1);
        assertThat(authSubjects.getFirst()).isEqualTo(AuthorizationSubject.newInstance(subjectIssuer + ":" + tokenSubject));
    }

}
