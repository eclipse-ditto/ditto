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
package org.eclipse.ditto.gateway.service.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.util.config.security.NamespaceAccessConfig;
import org.junit.Test;

/**
 * Unit tests for {@link NamespaceAccessValidatorFactory}.
 */
public class NamespaceAccessValidatorFactoryTest {

    private static final NamespaceAccessConfig JWT_RULE = NamespaceAccessTestSupport.config(
            List.of("{{ jwt:iss | fn:filter('like','https://eclipse.org*') }}"),
            List.of("org.eclipse.*"),
            List.of()
    );

    @Test
    public void parsesBearerTokenFromHeaders() {
        final String jwtToken = NamespaceAccessTestSupport.jwt("{\"iss\":\"https://eclipse.org/auth\"}").getToken();
        final DittoHeaders dittoHeaders = NamespaceAccessTestSupport.newHeadersWithHeader(
                DittoHeaderDefinition.AUTHORIZATION.getKey(),
                "Bearer " + jwtToken
        );

        final NamespaceAccessValidatorFactory underTest = new NamespaceAccessValidatorFactory(List.of(JWT_RULE));
        final NamespaceAccessValidator validator = underTest.createValidator(dittoHeaders, "thing");

        assertThat(validator.isNamespaceAccessible("org.eclipse.demo")).isTrue();
        assertThat(validator.isNamespaceAccessible("com.acme")).isFalse();
    }

    @Test
    public void nonBearerAuthorizationHeaderDeniesAccessFailClosed() {
        final DittoHeaders dittoHeaders = NamespaceAccessTestSupport.newHeadersWithHeader(
                DittoHeaderDefinition.AUTHORIZATION.getKey(),
                "Basic abc123"
        );

        final NamespaceAccessValidatorFactory underTest = new NamespaceAccessValidatorFactory(List.of(JWT_RULE));
        final NamespaceAccessValidator validator = underTest.createValidator(dittoHeaders, "thing");

        assertThat(validator.isNamespaceAccessible("org.eclipse.demo")).isFalse();
        assertThat(validator.isNamespaceAccessible("com.acme")).isFalse();
    }

    @Test
    public void malformedAuthorizationHeaderDeniesAccessFailClosed() {
        final DittoHeaders dittoHeaders = NamespaceAccessTestSupport.newHeadersWithHeader(
                DittoHeaderDefinition.AUTHORIZATION.getKey(),
                "invalid-auth-header"
        );

        final NamespaceAccessValidatorFactory underTest = new NamespaceAccessValidatorFactory(List.of(JWT_RULE));
        final NamespaceAccessValidator validator = underTest.createValidator(dittoHeaders, "thing");

        assertThat(validator.isNamespaceAccessible("org.eclipse.demo")).isFalse();
        assertThat(validator.isNamespaceAccessible("com.acme")).isFalse();
    }

    @Test
    public void malformedBearerTokenDeniesAccessFailClosed() {
        final DittoHeaders dittoHeaders = NamespaceAccessTestSupport.newHeadersWithHeader(
                DittoHeaderDefinition.AUTHORIZATION.getKey(),
                "Bearer not-a-jwt"
        );

        final NamespaceAccessValidatorFactory underTest = new NamespaceAccessValidatorFactory(List.of(JWT_RULE));
        final NamespaceAccessValidator validator = underTest.createValidator(dittoHeaders, "thing");

        assertThat(validator.isNamespaceAccessible("org.eclipse.demo")).isFalse();
        assertThat(validator.isNamespaceAccessible("com.acme")).isFalse();
    }

    @Test
    public void filtersRulesByResourceType() {
        final NamespaceAccessConfig thingOnlyRule = NamespaceAccessTestSupport.config(
                List.of(),
                List.of("thing.ns.*"),
                List.of(),
                List.of("thing")
        );
        final NamespaceAccessConfig policyOnlyRule = NamespaceAccessTestSupport.config(
                List.of(),
                List.of("policy.ns.*"),
                List.of(),
                List.of("policy")
        );

        final NamespaceAccessValidatorFactory underTest =
                new NamespaceAccessValidatorFactory(List.of(thingOnlyRule, policyOnlyRule));

        final NamespaceAccessValidator thingValidator = underTest.createValidator(
                NamespaceAccessTestSupport.newHeaders(), "thing");
        assertThat(thingValidator.isNamespaceAccessible("thing.ns.demo")).isTrue();
        assertThat(thingValidator.isNamespaceAccessible("policy.ns.demo")).isFalse();

        final NamespaceAccessValidator policyValidator = underTest.createValidator(
                NamespaceAccessTestSupport.newHeaders(), "policy");
        assertThat(policyValidator.isNamespaceAccessible("thing.ns.demo")).isFalse();
        assertThat(policyValidator.isNamespaceAccessible("policy.ns.demo")).isTrue();
    }

    @Test
    public void ruleWithEmptyResourceTypesAppliesToAll() {
        final NamespaceAccessConfig allResourcesRule = NamespaceAccessTestSupport.config(
                List.of(),
                List.of("shared.ns.*"),
                List.of()
        );

        final NamespaceAccessValidatorFactory underTest =
                new NamespaceAccessValidatorFactory(List.of(allResourcesRule));

        assertThat(underTest.createValidator(NamespaceAccessTestSupport.newHeaders(), "thing")
                .isNamespaceAccessible("shared.ns.demo")).isTrue();
        assertThat(underTest.createValidator(NamespaceAccessTestSupport.newHeaders(), "policy")
                .isNamespaceAccessible("shared.ns.demo")).isTrue();
    }

}
