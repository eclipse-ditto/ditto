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
package org.eclipse.ditto.model.things;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.entity.id.NamespacedEntityId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ThingIdTest {

    @Test
    public void testImmutability() {
        assertInstancesOf(ThingId.class, areImmutable(), provided(NamespacedEntityId.class).isAlsoImmutable());
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(ThingId.class).verify();
    }

    @Test
    public void invalidNamespaceThrowsThingInvalidException() {
        assertThatExceptionOfType(ThingIdInvalidException.class)
                .isThrownBy(() -> ThingId.of(".invalidNamespace", "validName"))
                .matches(thingIdInvalidException -> {
                    assertThat(thingIdInvalidException.getDescription())
                            .contains("The namespace prefix must conform the syntax of the java package notation " +
                                    "and must end with a colon (':').");
                    return true;
                });

        assertThatExceptionOfType(ThingIdInvalidException.class)
                .isThrownBy(() -> ThingId.of(".invalidNamespace:validName"))
                .matches(thingIdInvalidException -> {
                    assertThat(thingIdInvalidException.getDescription())
                            .contains("It must contain a namespace prefix (java package notation + a colon ':') + " +
                                    "a name and must be a valid URI path segment according to RFC-3986");
                    return true;
                });
    }

    @Test
    public void invalidNameThrowsThingInvalidException() {
        assertThatExceptionOfType(ThingIdInvalidException.class)
                .isThrownBy(() -> ThingId.of("validNamespace", "§inValidName"))
                .matches(thingIdInvalidException -> {
                    assertThat(thingIdInvalidException.getDescription())
                            .contains("The name of the thing was not valid. It must be a valid URI path segment " +
                                    "according to RFC-3986");
                    return true;
                });

        assertThatExceptionOfType(ThingIdInvalidException.class)
                .isThrownBy(() -> ThingId.inDefaultNamespace("§inValidName"))
                .matches(thingIdInvalidException -> {
                    assertThat(thingIdInvalidException.getDescription())
                            .contains("The name of the thing was not valid. It must be a valid URI path segment " +
                                    "according to RFC-3986");
                    return true;
                });

        assertThatExceptionOfType(ThingIdInvalidException.class)
                .isThrownBy(() -> ThingId.of("validNamespace:§inValidName"))
                .matches(thingIdInvalidException -> {
                    assertThat(thingIdInvalidException.getDescription())
                            .contains("It must contain a namespace prefix (java package notation + a colon ':') + " +
                                    "a name and must be a valid URI path segment according to RFC-3986");
                    return true;
                });
    }

    @Test
    public void placeHolderIsPlaceHolder() {
        assertThat(ThingId.placeholder().isPlaceholder()).isTrue();
    }

    @Test
    public void manuallyCreatedPlaceHolderIsPlaceHolder() {
        assertThat(ThingId.of("unknown", "unknown").isPlaceholder()).isTrue();
        assertThat(ThingId.of("unknown:unknown").isPlaceholder()).isTrue();
    }

    @Test
    public void validThingIdIsNoPlaceHolder() {
        assertThat(ThingId.of("namespace", "name").isPlaceholder()).isFalse();
        assertThat(ThingId.of("namespace:name").isPlaceholder()).isFalse();
    }

    @Test
    public void toStringConcatenatesNamespaceAndName() {
        assertThat(ThingId.of("namespace", "name").toString()).isEqualTo("namespace:name");
        assertThat(ThingId.of("namespace:name").toString()).isEqualTo("namespace:name");
    }

    @Test
    public void returnsCorrectNamespace() {
        assertThat(ThingId.of("namespace", "name").getNamespace()).isEqualTo("namespace");
        assertThat(ThingId.of("namespace:name").getNamespace()).isEqualTo("namespace");
    }

    @Test
    public void returnsCorrectName() {
        assertThat(ThingId.of("namespace", "name").getName()).isEqualTo("name");
        assertThat(ThingId.of("namespace:name").getName()).isEqualTo("name");
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