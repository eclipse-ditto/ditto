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
package org.eclipse.ditto.policies.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class PolicyIdTest {

    @Test
    public void testImmutability() {
        assertInstancesOf(PolicyId.class, areImmutable(), provided(NamespacedEntityId.class).isAlsoImmutable());
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(PolicyId.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                //already contained in string representation which is compared in base class
                .withIgnoredFields("name", "namespace")
                .verify();
    }

    @Test
    public void instantiationFromEntityTypeCreatesPolicyId() {
        final NamespacedEntityId namespacedEntityId =
                NamespacedEntityId.of(PolicyConstants.ENTITY_TYPE, "namespace:name");
        final EntityId entityId = EntityId.of(PolicyConstants.ENTITY_TYPE, "namespace:name");

        assertThat((CharSequence) namespacedEntityId).isInstanceOf(PolicyId.class);
        assertThat((CharSequence) entityId).isInstanceOf(PolicyId.class);
    }

    @Test
    public void invalidNamespaceThrowsPolicyIdInvalidException() {
        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> PolicyId.of(".invalidNamespace", "validName"));

        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> PolicyId.inNamespaceWithRandomName(".invalidNamespace"));

        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> PolicyId.of(".invalidNamespace:validName"));
    }

    @Test
    public void invalidNameThrowsPolicyIdInvalidException() {
        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> PolicyId.of("validNamespace", "§inValidName"));

        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> PolicyId.of("validNamespace:§inValidName"));
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
