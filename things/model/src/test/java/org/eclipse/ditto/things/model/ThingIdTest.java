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
package org.eclipse.ditto.things.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertEquals;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.junit.Rule;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ThingId}.
 */
public final class ThingIdTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void testImmutability() {
        assertInstancesOf(ThingId.class,
                areImmutable(),
                provided(NamespacedEntityId.class).isAlsoImmutable());
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(ThingId.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                //already contained in string representation which is compared in base class
                .withIgnoredFields("name", "namespace")
                .verify();
    }
    @Test
    public void generateNamespaceTest() {
        final String namespace = "my.cool.namespace";
        final ThingId randomThingId = ThingId.generateRandom(namespace);
        assertEquals(randomThingId.getNamespace(), namespace);
    }
    @Test
    public void instantiationFromEntityTypeCreatesThingId() {
        final NamespacedEntityId namespacedEntityId =
                NamespacedEntityId.of(ThingConstants.ENTITY_TYPE, "namespace:name");
        final EntityId entityId = EntityId.of(ThingConstants.ENTITY_TYPE, "namespace:name");

        assertThat((CharSequence) namespacedEntityId).isInstanceOf(ThingId.class);
        assertThat((CharSequence) entityId).isInstanceOf(ThingId.class);
    }

    @Test
    public void fromNameHasEmptyNamespace() {
        final NamespacedEntityId namespacedEntityId = ThingId.inDefaultNamespace("validName");
        assertThat(namespacedEntityId.getNamespace()).isEmpty();
    }

    @Test
    public void invalidNamespaceThrowsThingInvalidException() {
        assertThatExceptionOfType(ThingIdInvalidException.class)
                .isThrownBy(() -> ThingId.of(".invalidNamespace", "validName"));

        assertThatExceptionOfType(ThingIdInvalidException.class)
                .isThrownBy(() -> ThingId.of(".invalidNamespace:validName"));
    }

    @Test
    public void invalidNameThrowsThingInvalidException() {
        assertThatExceptionOfType(ThingIdInvalidException.class)
                .isThrownBy(() -> ThingId.of("validNamespace", "§inValidName"));

        assertThatExceptionOfType(ThingIdInvalidException.class)
                .isThrownBy(() -> ThingId.inDefaultNamespace("§inValidName"));

        assertThatExceptionOfType(ThingIdInvalidException.class)
                .isThrownBy(() -> ThingId.of("validNamespace:§inValidName"));
    }

    @Test
    public void toStringConcatenatesNamespaceAndName() {
        softly.assertThat(ThingId.of("namespace", "name").toString()).isEqualTo("namespace:name");
        softly.assertThat(ThingId.of("namespace:name").toString()).isEqualTo("namespace:name");
    }

    @Test
    public void returnsCorrectNamespace() {
        softly.assertThat(ThingId.of("namespace", "name").getNamespace()).isEqualTo("namespace");
        softly.assertThat(ThingId.of("namespace:name").getNamespace()).isEqualTo("namespace");
    }

    @Test
    public void returnsCorrectName() {
        softly.assertThat(ThingId.of("namespace", "name").getName()).isEqualTo("name");
        softly.assertThat(ThingId.of("namespace:name").getName()).isEqualTo("name");
    }

    @Test
    public void generateRandomHasEmptyNamespace() {
        final ThingId randomThingId = ThingId.generateRandom();
        assertThat(randomThingId.getNamespace()).isEmpty();
    }

    @Test
    public void thingIdOfThingIdReturnsSameInstance() {
        final ThingId thingIdOne = ThingId.of("namespace", "name");
        final ThingId thingIdTwo = ThingId.of(thingIdOne);

        assertThat((CharSequence) thingIdOne).isSameAs(thingIdTwo);
    }

}
