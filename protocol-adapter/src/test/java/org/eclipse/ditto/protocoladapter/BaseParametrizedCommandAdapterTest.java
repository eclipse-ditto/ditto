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
package org.eclipse.ditto.protocoladapter;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.base.Signal;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Base class for parameterized command adapter tests.
 */
@RunWith(Parameterized.class)
public abstract class BaseParametrizedCommandAdapterTest<T extends Signal> implements ProtocolAdapterTest {

    static final JsonPointer EMPTY_PATH = JsonPointer.empty();

    @Parameterized.Parameter(0)
    public String name;

    @Parameterized.Parameter(1)
    public Adaptable adaptable;

    @Parameterized.Parameter(2)
    public T command;

    protected abstract AbstractPolicyAdapter<T> underTest();

    @Test
    public void fromAdaptable() {
        final T actual = underTest().fromAdaptable(adaptable);
        assertWithExternalHeadersThat(actual).isEqualTo(command);
    }

    @Test
    public void toAdaptable() {
        final Adaptable actual = underTest().toAdaptable(command);
        assertWithExternalHeadersThat(actual).isEqualTo(adaptable);
    }

    @Test
    public void adaptableToCommandToAdaptable() {
        final T fromAdaptable = underTest().fromAdaptable(adaptable);
        final Adaptable fromAdaptableToAdaptable = underTest().toAdaptable(fromAdaptable);
        assertWithExternalHeadersThat(fromAdaptableToAdaptable).isEqualTo(adaptable);
    }

    @Test
    public void commandToAdaptableToCommand() {
        final Adaptable toAdaptable = underTest().toAdaptable(command);
        final T toAdaptableFromAdaptable = underTest().fromAdaptable(toAdaptable);
        assertWithExternalHeadersThat(toAdaptableFromAdaptable).isEqualTo(command);
    }

    static Collection<Object[]> toObjects(final TestParameter<?>... parameters) {
        return Stream.of(parameters).map(TestParameter::toObject).collect(Collectors.toList());
    }

    static JsonPointer newPointer(final String... segments) {
        return Arrays.stream(segments).reduce(JsonPointer.empty(),
                (p, segment) -> p.addLeaf(JsonKey.of(segment)),
                JsonPointer::append);
    }

    static <T> JsonValue fromIterable(final Iterable<T> source, final Function<T, JsonKey> key,
            final Function<T, JsonValue> value) {
        return StreamSupport.stream(source.spliterator(), false)
                .map(t -> JsonFactory.newField(key.apply(t), value.apply(t)))
                .collect(JsonCollectors.fieldsToObject());
    }

    static JsonPointer entriesPath() {
        return newPointer("entries");
    }

    static JsonPointer entriesPath(final Label label) {
        return newPointer("entries", label.toString());
    }

    static JsonPointer resourcesPath(final Label label) {
        return newPointer("entries", label.toString(), "resources");
    }

    static JsonPointer resourcesPath(final Label label, final ResourceKey resourceKey) {
        return newPointer("entries", label.toString(), "resources").append(JsonPointer.of(resourceKey));
    }

    static JsonPointer subjectsPath(final Label label) {
        return newPointer("entries", label.toString(), "subjects");
    }

    static JsonPointer subjectsPath(final Label label, final SubjectId subjectId) {
        return newPointer("entries", label.toString(), "subjects", subjectId.toString());
    }

    static class TestParameter<T> {

        private final String name;
        private final Adaptable adaptable;
        private final T command;

        private TestParameter(final String name, final Adaptable adaptable, final T command) {
            this.name = name;
            this.adaptable = adaptable;
            this.command = command;
        }

        static <C extends Jsonifiable & WithDittoHeaders> TestParameter<C> of(final String name,
                final Adaptable adaptable, final C command) {
            return new TestParameter<>(name, adaptable, command);
        }

        Object[] toObject() {
            return new Object[]{name, adaptable, command};
        }
    }
}
