/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.headers.conditional;


import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.things.Thing;
import org.junit.Test;

public class ETagValueGeneratorTest {

    @Test
    public void generateForPolicy() {
        Policy policy = Policy.newBuilder("Test:Test").setRevision(4711).build();

        final CharSequence generatedETagValue = ETagValueGenerator.generate(policy).get();

        assertThat(generatedETagValue).isEqualTo("4711");
    }

    @Test
    public void generateForThing() {
        Thing thing = Thing.newBuilder().setId("Test:Test").setRevision(1337).build();

        final CharSequence generatedETagValue = ETagValueGenerator.generate(thing).get();

        assertThat(generatedETagValue).isEqualTo("1337");
    }

    @Test
    public void generateForObject() {
        String someObject = "1234";

        final CharSequence generatedETagValue = ETagValueGenerator.generate(someObject).get();

        String expectedETagValue = String.valueOf("1234".hashCode());
        assertThat(generatedETagValue).isEqualTo(expectedETagValue);
    }

    @Test
    public void generateForNull() {

        final Optional<CharSequence> generatedETagValue = ETagValueGenerator.generate(null);

        assertThat(generatedETagValue).isNotPresent();
    }
}