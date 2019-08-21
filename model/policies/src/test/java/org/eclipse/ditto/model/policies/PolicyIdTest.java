/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.policies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.entity.id.NamespacedEntityId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class PolicyIdTest {

    @Test
    public void testImmutability() {
        assertInstancesOf(PolicyId.class, areImmutable(), provided(NamespacedEntityId.class).isAlsoImmutable());
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(PolicyId.class).verify();
    }

    @Test
    public void invalidNamespaceThrowsPolicyIdInvalidException() {
        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> PolicyId.of(".invalidNamespace", "validName"))
                .matches(policyIdInvalidException -> {
                    assertThat(policyIdInvalidException.getDescription())
                            .contains("The namespace prefix must conform the syntax of the java package notation " +
                                    "and must end with a colon (':').");
                    return true;
                });

        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> PolicyId.inNamespaceWithRandomName(".invalidNamespace"))
                .matches(policyIdInvalidException -> {
                    assertThat(policyIdInvalidException.getDescription())
                            .contains("The namespace prefix must conform the syntax of the java package notation " +
                                    "and must end with a colon (':').");
                    return true;
                });

        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> PolicyId.of(".invalidNamespace:validName"))
                .matches(policyIdInvalidException -> {
                    assertThat(policyIdInvalidException.getDescription())
                            .contains("It must contain a namespace prefix (java package notation + a colon ':') + " +
                                    "a name and must be a valid URI path segment according to RFC-3986");
                    return true;
                });
    }

    @Test
    public void invalidNameThrowsPolicyIdInvalidException() {
        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> PolicyId.of("validNamespace", "§inValidName"))
                .matches(policyIdInvalidException -> {
                    assertThat(policyIdInvalidException.getDescription())
                            .contains("The name of the policy was not valid. It must be a valid URI path segment " +
                                    "according to RFC-3986");
                    return true;
                });

        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> PolicyId.of("validNamespace:§inValidName"))
                .matches(policyIdInvalidException -> {
                    assertThat(policyIdInvalidException.getDescription())
                            .contains("It must contain a namespace prefix (java package notation + a colon ':') + " +
                                    "a name and must be a valid URI path segment according to RFC-3986");
                    return true;
                });
    }

    @Test
    public void placeHolderIsPlaceHolder() {
        assertThat(PolicyId.placeholder().isPlaceholder()).isTrue();
    }

    @Test
    public void manuallyCreatedPlaceHolderIsPlaceHolder() {
        assertThat(PolicyId.of("unknown", "unknown").isPlaceholder()).isTrue();
        assertThat(PolicyId.of("unknown:unknown").isPlaceholder()).isTrue();
    }

    @Test
    public void validPolicyIdIsNoPlaceHolder() {
        assertThat(PolicyId.of("namespace", "name").isPlaceholder()).isFalse();
        assertThat(PolicyId.of("namespace:name").isPlaceholder()).isFalse();
    }

    @Test
    public void toStringConcatenatesNamespaceAndName() {
        assertThat(PolicyId.of("namespace", "name").toString()).isEqualTo("namespace:name");
        assertThat(PolicyId.of("namespace:name").toString()).isEqualTo("namespace:name");
    }

    @Test
    public void returnsCorrectNamespace() {
        assertThat(PolicyId.of("namespace", "name").getNamespace()).isEqualTo("namespace");
        assertThat(PolicyId.of("namespace:name").getNamespace()).isEqualTo("namespace");
    }

    @Test
    public void returnsCorrectName() {
        assertThat(PolicyId.of("namespace", "name").getName()).isEqualTo("name");
        assertThat(PolicyId.of("namespace:name").getName()).isEqualTo("name");
    }

    @Test
    public void inNamespaceWithRandomNameHasCorrectNamespace() {
        final PolicyId randomPolicyId = PolicyId.inNamespaceWithRandomName("namespace");

        assertThat(randomPolicyId.getNamespace()).isEqualTo("namespace");
        assertThat(randomPolicyId.getName()).isNotEmpty();
    }

    @Test
    public void policyIdOfPolicyIdReturnsSameInstance() {
        final PolicyId policyIdOne = PolicyId.of("namespace", "name");
        final PolicyId policyIdTwo = PolicyId.of(policyIdOne);

        assertThat((CharSequence) policyIdOne).isSameAs(policyIdTwo);
    }

}