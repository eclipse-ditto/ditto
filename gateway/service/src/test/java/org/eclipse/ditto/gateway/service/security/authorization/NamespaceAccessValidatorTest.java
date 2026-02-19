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
import java.util.Set;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.util.config.security.NamespaceAccessConfig;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.junit.Test;

/**
 * Unit tests for {@link NamespaceAccessValidator}.
 */
public class NamespaceAccessValidatorTest {

    private static final DittoHeaders DITTO_HEADERS = NamespaceAccessTestSupport.newHeaders();

    @Test
    public void testNoConfigurationsAllowsAllNamespaces() {
        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(),
                DITTO_HEADERS,
                null
        );

        assertThat(validator.isNamespaceAccessible("org.eclipse.ditto")).isTrue();
        assertThat(validator.isNamespaceAccessible("any.namespace")).isTrue();
    }

    @Test
    public void testAllowedNamespacePatterns() {
        final NamespaceAccessConfig config = NamespaceAccessTestSupport.config(
                List.of(), // no conditions, always applies
                List.of("org.eclipse.*"),
                List.of()
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(config),
                DITTO_HEADERS,
                null
        );

        assertThat(validator.isNamespaceAccessible("org.eclipse.ditto")).isTrue();
        assertThat(validator.isNamespaceAccessible("com.example.test")).isFalse();
    }

    @Test
    public void testBlockedNamespacePatterns() {
        final NamespaceAccessConfig config = NamespaceAccessTestSupport.config(
                List.of(),
                List.of("org.eclipse.*"),
                List.of("org.eclipse.test*")
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(config),
                DITTO_HEADERS,
                null
        );

        assertThat(validator.isNamespaceAccessible("org.eclipse.ditto")).isTrue();
        assertThat(validator.isNamespaceAccessible("org.eclipse.test.internal")).isFalse();
    }

    @Test
    public void testFilterAllowedNamespaces() {
        final NamespaceAccessConfig config = NamespaceAccessTestSupport.config(
                List.of(),
                List.of("org.eclipse.*", "com.example.*"),
                List.of()
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(config),
                DITTO_HEADERS,
                null
        );

        final Set<String> requestedNamespaces = Set.of(
                "org.eclipse.ditto",
                "org.eclipse.things",
                "com.example.test",
                "net.forbidden.namespace"
        );

        final Set<String> filteredNamespaces = validator.filterAllowedNamespaces(requestedNamespaces);

        assertThat(filteredNamespaces).containsExactlyInAnyOrder(
                "org.eclipse.ditto",
                "org.eclipse.things",
                "com.example.test"
        );
        assertThat(filteredNamespaces).doesNotContain("net.forbidden.namespace");
    }

    @Test
    public void testGetApplicableNamespacePatternsReturnsEmptyForWildcardAllowedNamespaces() {
        final NamespaceAccessConfig config = NamespaceAccessTestSupport.config(
                List.of(), // no conditions for this test
                List.of("org.eclipse.*"),
                List.of()
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(config),
                DITTO_HEADERS,
                null
        );

        assertThat(validator.getApplicableNamespacePatterns()).isEmpty();
    }

    @Test
    public void testGetApplicableNamespacePatternsWithExactAllowedNamespace() {
        final NamespaceAccessConfig config = NamespaceAccessTestSupport.config(
                List.of(),
                List.of("org.eclipse.demo"),
                List.of()
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(config),
                DITTO_HEADERS,
                null
        );

        final Set<String> patterns = validator.getApplicableNamespacePatterns().orElse(Set.of());
        assertThat(patterns).containsExactly("org.eclipse.demo");
    }

    @Test
    public void testGetApplicableNamespacePatternsReturnsEmptyWhenAllowedIsEmpty() {
        final NamespaceAccessConfig config = NamespaceAccessTestSupport.config(
                List.of(),
                List.of(), // empty allowed list means no restrictions
                List.of()
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(config),
                DITTO_HEADERS,
                null
        );

        assertThat(validator.getApplicableNamespacePatterns()).isEmpty();
    }

    @Test
    public void testMultipleConfigsWithDifferentPatterns() {
        final NamespaceAccessConfig config1 = NamespaceAccessTestSupport.config(
                List.of("{{ header:x-tenant | fn:filter('eq','tenant-a') }}"),
                List.of("org.eclipse.*"),
                List.of()
        );

        final NamespaceAccessConfig config2 = NamespaceAccessTestSupport.config(
                List.of("{{ header:x-tenant | fn:filter('eq','tenant-b') }}"),
                List.of("com.example.*"),
                List.of()
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(config1, config2),
                NamespaceAccessTestSupport.newHeadersWithHeader("x-tenant", "tenant-a"),
                null
        );

        assertThat(validator.isNamespaceAccessible("org.eclipse.ditto")).isTrue();
        assertThat(validator.isNamespaceAccessible("com.example.test")).isFalse();
        assertThat(validator.isNamespaceAccessible("net.other.namespace")).isFalse();
    }

    @Test
    public void testMultipleMatchingRulesUseOrSemanticsForAllowedNamespaces() {
        final NamespaceAccessConfig tenantRule = NamespaceAccessTestSupport.config(
                List.of("{{ header:x-tenant | fn:filter('eq','tenant-a') }}"),
                List.of("com.tenant.*"),
                List.of()
        );
        final NamespaceAccessConfig fallbackRule = NamespaceAccessTestSupport.config(
                List.of(),
                List.of("org.eclipse.*"),
                List.of("org.eclipse.test.blocked")
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(tenantRule, fallbackRule),
                NamespaceAccessTestSupport.newHeadersWithHeader("x-tenant", "tenant-a"),
                null
        );

        assertThat(validator.isNamespaceAccessible("com.tenant.device")).isTrue();
        assertThat(validator.isNamespaceAccessible("org.eclipse.demo")).isTrue();
        assertThat(validator.isNamespaceAccessible("com.acme")).isFalse();
    }

    @Test
    public void testBlockedNamespaceHasPrecedenceAcrossMatchingRules() {
        final NamespaceAccessConfig allowAllForTenant = NamespaceAccessTestSupport.config(
                List.of("{{ header:x-tenant | fn:filter('eq','tenant-a') }}"),
                List.of(),
                List.of("forbidden.namespace")
        );
        final NamespaceAccessConfig explicitAllow = NamespaceAccessTestSupport.config(
                List.of(),
                List.of("forbidden.*"),
                List.of()
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(allowAllForTenant, explicitAllow),
                NamespaceAccessTestSupport.newHeadersWithHeader("x-tenant", "tenant-a"),
                null
        );

        assertThat(validator.isNamespaceAccessible("forbidden.namespace")).isFalse();
        assertThat(validator.isNamespaceAccessible("forbidden.other")).isTrue();
    }

    @Test
    public void testJwtFnFilterConditionAllowsMatchingIssuer() {
        final NamespaceAccessConfig config = NamespaceAccessTestSupport.config(
                List.of("{{ jwt:iss | fn:filter('like','https://eclipse.org*') }}"),
                List.of("org.eclipse.*"),
                List.of()
        );
        final JsonWebToken jwt = NamespaceAccessTestSupport.jwt(
                "{\"iss\":\"https://eclipse.org/auth\",\"sub\":\"eclipse-user\"}"
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(config),
                DITTO_HEADERS,
                jwt
        );

        assertThat(validator.isNamespaceAccessible("org.eclipse.demo")).isTrue();
        assertThat(validator.isNamespaceAccessible("com.acme")).isFalse();
    }

    @Test
    public void testHeaderFnFilterConditionAllowsMatchingHeader() {
        final NamespaceAccessConfig config = NamespaceAccessTestSupport.config(
                List.of("{{ header:x-tenant | fn:filter('eq','tenant-a') }}"),
                List.of("org.eclipse.*"),
                List.of()
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(config),
                NamespaceAccessTestSupport.newHeadersWithHeader("x-tenant", "tenant-a"),
                null
        );

        assertThat(validator.isNamespaceAccessible("org.eclipse.demo")).isTrue();
        assertThat(validator.isNamespaceAccessible("com.acme")).isFalse();
    }

    @Test
    public void testHeaderFnFilterConditionAllowsNonEmptyHeader() {
        final NamespaceAccessConfig config = NamespaceAccessTestSupport.config(
                List.of("{{ header:x-tenant | fn:filter('ne','') }}"),
                List.of("org.eclipse.*"),
                List.of()
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(config),
                NamespaceAccessTestSupport.newHeadersWithHeader("x-tenant", "tenant-a"),
                null
        );

        assertThat(validator.isNamespaceAccessible("org.eclipse.demo")).isTrue();
        assertThat(validator.isNamespaceAccessible("com.acme")).isFalse();
    }

    @Test
    public void testHeaderRuleWithExactAndWildcardAllowsBaseAndSubNamespace() {
        final NamespaceAccessConfig config = NamespaceAccessTestSupport.config(
                List.of("{{ header:x-tenant | fn:filter('like','tenant-*') }}"),
                List.of("com.tenant", "com.tenant.*"),
                List.of()
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(config),
                NamespaceAccessTestSupport.newHeadersWithHeader("x-tenant", "tenant-a"),
                null
        );

        assertThat(validator.isNamespaceAccessible("com.tenant")).isTrue();
        assertThat(validator.isNamespaceAccessible("com.tenant.sub")).isTrue();
    }

    @Test
    public void testConditionRetainsNoValuesMeansRuleNotFulfilled() {
        final NamespaceAccessConfig restrictedRule = NamespaceAccessTestSupport.config(
                List.of("{{ header:x-tenant | fn:filter('eq','tenant-a') }}"),
                List.of("org.eclipse.*"),
                List.of()
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(restrictedRule),
                NamespaceAccessTestSupport.newHeadersWithHeader("x-tenant", "tenant-b"),
                null
        );

        // Condition does not retain any value, therefore rule does not apply.
        assertThat(validator.isNamespaceAccessible("com.acme")).isTrue();
    }

    @Test
    public void testTimePlaceholderConditionIsSupported() {
        final NamespaceAccessConfig config = NamespaceAccessTestSupport.config(
                List.of("{{ time:now | fn:filter('like','*') }}"),
                List.of("org.eclipse.*"),
                List.of()
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(config),
                DITTO_HEADERS,
                null
        );

        assertThat(validator.isNamespaceAccessible("org.eclipse.demo")).isTrue();
    }

    @Test
    public void testCombinedJwtAndHeaderConditionsMatchWithAndSemantics() {
        final NamespaceAccessConfig combinedRule = NamespaceAccessTestSupport.config(
                List.of(
                        "{{ jwt:iss | fn:filter('like','http://localhost:9900/eclipse*') }}",
                        "{{ header:someheader | fn:filter('ne','dangerous') }}"
                ),
                List.of("another.concrete.namespace"),
                List.of()
        );
        final NamespaceAccessConfig fallbackRule = NamespaceAccessTestSupport.config(
                List.of(),
                List.of("org.eclipse.*"),
                List.of()
        );
        final JsonWebToken jwt = NamespaceAccessTestSupport.jwt(
                "{\"iss\":\"http://localhost:9900/eclipse\",\"sub\":\"eclipse-user\"}"
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(combinedRule, fallbackRule),
                NamespaceAccessTestSupport.newHeadersWithHeader("someheader", "safe"),
                jwt
        );

        assertThat(validator.isNamespaceAccessible("another.concrete.namespace")).isTrue();
        assertThat(validator.isNamespaceAccessible("org.eclipse.demo")).isTrue();
    }

    @Test
    public void testCombinedJwtAndHeaderConditionsFailWhenOneConditionDoesNotRetainValue() {
        final NamespaceAccessConfig combinedRule = NamespaceAccessTestSupport.config(
                List.of(
                        "{{ jwt:iss | fn:filter('like','http://localhost:9900/eclipse*') }}",
                        "{{ header:someheader | fn:filter('ne','dangerous') }}"
                ),
                List.of("another.concrete.namespace"),
                List.of()
        );
        final NamespaceAccessConfig fallbackRule = NamespaceAccessTestSupport.config(
                List.of(),
                List.of("org.eclipse.*"),
                List.of()
        );
        final JsonWebToken jwt = NamespaceAccessTestSupport.jwt(
                "{\"iss\":\"http://localhost:9900/eclipse\",\"sub\":\"eclipse-user\"}"
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(combinedRule, fallbackRule),
                NamespaceAccessTestSupport.newHeadersWithHeader("someheader", "dangerous"),
                jwt
        );

        assertThat(validator.isNamespaceAccessible("another.concrete.namespace")).isFalse();
        assertThat(validator.isNamespaceAccessible("org.eclipse.demo")).isTrue();
    }

    @Test
    public void testJwtConditionWithoutJwtDoesNotFailAndRuleIsNotApplied() {
        final NamespaceAccessConfig jwtRule = NamespaceAccessTestSupport.config(
                List.of("{{ jwt:iss | fn:filter('like','http://localhost:9900/eclipse*') }}"),
                List.of("org.eclipse.*"),
                List.of()
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(jwtRule),
                DITTO_HEADERS,
                null
        );

        assertThat(validator.isNamespaceAccessible("org.eclipse.demo")).isTrue();
        assertThat(validator.isNamespaceAccessible("com.acme")).isTrue();
    }

    @Test
    public void testGetApplicableNamespacePatternsWithJwtConditionWithoutJwtReturnsEmpty() {
        final NamespaceAccessConfig jwtRule = NamespaceAccessTestSupport.config(
                List.of("{{ jwt:iss | fn:filter('like','http://localhost:9900/eclipse*') }}"),
                List.of("org.eclipse.demo"),
                List.of()
        );

        final NamespaceAccessValidator validator = new NamespaceAccessValidator(
                List.of(jwtRule),
                DITTO_HEADERS,
                null
        );

        assertThat(validator.getApplicableNamespacePatterns()).isEmpty();
    }

}
