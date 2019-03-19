/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.concurrent.Immutable;

import org.atteo.classindex.ClassIndex;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.junit.Before;
import org.junit.Test;
import org.mutabilitydetector.internal.javassist.Modifier;

@Immutable
public abstract class GlobalCommandResponseRegistryTestCases {

    private final List<Class<?>> samples;
    private List<Class<?>> jsonParsableCommandResponses;

    @Before
    public void setup() {
        jsonParsableCommandResponses =
                StreamSupport.stream(ClassIndex.getAnnotated(JsonParsableCommandResponse.class).spliterator(), true)
                        .filter(c -> !c.getSimpleName().equals("TestCommandResponse"))
                        .collect(Collectors.toList());
    }

    /**
     * Creates a new instance of test cases.
     *
     * @param samples a List of classes that are annotated with {@link JsonParsableCommandResponse}. This list should contain
     * at least one command of each package containing a CommandResponse in the classpath of the respective service.
     */
    protected GlobalCommandResponseRegistryTestCases(final List<Class<?>> samples) {
        this.samples = new ArrayList<>(samples);
    }

    /**
     * This test should verify that all modules containing commandresponses that need to be deserialized
     * in this service are still in the classpath. Therefore one sample of each module is placed in {@code samples}.
     */
    @Test
    public void sampleCheckForCommandResponseFromEachModule() {
        assertThat(jsonParsableCommandResponses).containsAll(samples);
    }

    /**
     * This test should enforce to keep {@code samples} up to date.
     * If this test fails add a command of each missing package to samples.
     */
    @Test
    public void ensureSamplesAreComplete() {
        final Set<Package> packagesOfAnnotated = getPackagesDistinct(jsonParsableCommandResponses);
        final Set<Package> packagesOfSamples = getPackagesDistinct(samples);

        assertThat(packagesOfSamples).containsAll(packagesOfAnnotated);
    }

    private static Set<Package> getPackagesDistinct(final Iterable<Class<?>> classes) {
        return StreamSupport.stream(classes.spliterator(), false)
                .map(Class::getPackage)
                .collect(Collectors.toSet());
    }

    @Test
    public void allCommandResponsesRegistered() {
        StreamSupport.stream(ClassIndex.getSubclasses(CommandResponse.class).spliterator(), true)
                .filter(c -> {
                    final int m = c.getModifiers();
                    return !(Modifier.isAbstract(m) || Modifier.isInterface(m));
                })
                .forEach(c -> assertThat(c.isAnnotationPresent(JsonParsableCommandResponse.class))
                        .as("Check that '%s' is annotated with JsonParsableCommandResponse.", c.getName())
                        .isTrue());
    }

    @Test
    public void allRegisteredCommandResponsesContainAMethodToParseFromJson() throws NoSuchMethodException {

        for (final Class<?> jsonParsableCommand : jsonParsableCommandResponses) {
            final JsonParsableCommandResponse annotation =
                    jsonParsableCommand.getAnnotation(JsonParsableCommandResponse.class);
            assertAnnotationIsValid(annotation, jsonParsableCommand);
        }
    }

    private static void assertAnnotationIsValid(final JsonParsableCommandResponse annotation, final Class<?> cls)
            throws NoSuchMethodException {
        assertThat(cls.getMethod(annotation.method(), JsonObject.class, DittoHeaders.class))
                .as("Check that JsonParsableCommandResponse of '%s' has correct methodName.", cls.getName())
                .isNotNull();
    }
}
