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
package org.eclipse.ditto.internal.utils.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.concurrent.Immutable;

import org.atteo.classindex.ClassIndex;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.junit.Before;
import org.junit.Test;
import org.mutabilitydetector.internal.javassist.Modifier;

@Immutable
public abstract class GlobalCommandResponseRegistryTestCases {

    private final List<Class<?>> samples;
    private final List<String> knownNotAnnotatedClassnames;
    private List<Class<?>> jsonParsableCommandResponses;

    /**
     * Creates a new instance of test cases.
     * The given classes should contain at least one command of each package containing a CommandResponse in the
     * classpath of the respective service.
     *
     * @param sample a class that is annotated with {@link JsonParsableCommandResponse}.
     * @param furtherSamples further classes that are annotated with {@link JsonParsableCommandResponse}.
     */
    protected GlobalCommandResponseRegistryTestCases(final Class<?> sample, final Class<?>... furtherSamples) {
        samples = new ArrayList<>(1 + furtherSamples.length);
        samples.add(sample);
        Collections.addAll(samples, furtherSamples);
        knownNotAnnotatedClassnames = new ArrayList<>();
    }

    @Before
    public void setup() {
        jsonParsableCommandResponses =
                StreamSupport.stream(ClassIndex.getAnnotated(JsonParsableCommandResponse.class).spliterator(), true)
                        .filter(c -> !"TestCommandResponse".equals(c.getSimpleName()))
                        .collect(Collectors.toList());
    }

    protected void excludeKnownNotAnnotatedClass(String className) {
        knownNotAnnotatedClassnames.add(className);
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
                .filter(c -> !knownNotAnnotatedClassnames.contains(c.getName()))
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
                .as("Check that JsonParsableCommandResponse of '%s' has correct method name.", cls.getName())
                .isNotNull();
    }

}
