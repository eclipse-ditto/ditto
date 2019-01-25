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
package org.eclipse.ditto.services.policies.persistence.actors.policies;

import static org.assertj.core.api.Assertions.assertThat;

import org.atteo.classindex.ClassIndex;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;
import org.junit.Test;

public final class GlobalErrorRegistryValidationTest {

    @Test
    public void allExceptionsRegistered() {
        final Iterable<Class<? extends DittoRuntimeException>> subclassesOfDRE =
                ClassIndex.getSubclasses(DittoRuntimeException.class);

        for (Class<? extends DittoRuntimeException> subclassOfDRE : subclassesOfDRE) {
            if (DittoJsonException.class.isAssignableFrom(subclassOfDRE)) {
                //DittoJsonException is another concept than the rest of DittoRuntimeExceptions
                continue;
            }
            assertThat(subclassOfDRE.isAnnotationPresent(JsonParsableException.class))
                    .as("Check that '%s' is annotated with JsonParsableException.", subclassOfDRE.getName())
                    .isTrue();
        }
    }

    @Test
    public void allRegisteredExceptionsContainAMethodToParseFromJson() throws NoSuchMethodException {
        final Iterable<Class<?>> jsonParsableExceptions = ClassIndex.getAnnotated(JsonParsableException.class);

        for (Class<?> jsonParsableException : jsonParsableExceptions) {
            final JsonParsableException annotation = jsonParsableException.getAnnotation(JsonParsableException.class);
            assertAnnotationIsValid(annotation, jsonParsableException);
        }
    }

    private void assertAnnotationIsValid(JsonParsableException annotation, Class<?> cls) throws NoSuchMethodException {
        assertThat(cls.getMethod(annotation.method(), JsonObject.class, DittoHeaders.class))
                .as("Check that JsonParsableException of '%s' has correct methodName.", cls.getName())
                .isNotNull();
    }
}
