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
package org.eclipse.ditto.model.base.assertions;

import java.util.Arrays;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;


/**
 * An abstract Assert for {@link Jsonifiable}s.
 */
public abstract class AbstractJsonifiableAssert<S extends AbstractAssert<S, T>, T extends Jsonifiable>
        extends AbstractAssert<S, T> {

    /**
     * Constructs a new {@code AbstractJsonifiableAssert} object.
     *
     * @param actual the Jsonifiable to be checked.
     * @param selfType the type of the actual assert.
     */
    protected AbstractJsonifiableAssert(final T actual, final Class<? extends AbstractJsonifiableAssert> selfType) {
        super(actual, selfType);
    }

    /**
     * Checks if the actual value returns the expected JSON string.
     *
     * @param expectedJsonString the JSON string the actual {@link Jsonifiable} is expected to return when {@link
     * Jsonifiable#toJsonString()} is called.
     * @return this assert to allow method chaining.
     */
    public S hasJsonString(final String expectedJsonString) {
        isNotNull();
        try {
            final String actualJsonString = actual.toJsonString();
            JSONAssert.assertEquals(expectedJsonString, actualJsonString, false);
        } catch (final JSONException e) {
            throw new AssertionError("JSONAssert failed to assert equality of actual and expected JSON string.", e);
        }
        return myself;
    }

    public S hasLatestSchemaVersion(final JsonSchemaVersion expectedLatestSchemaVersion) {
        isNotNull();
        final Object actualLatestSchemaVersion = actual.getLatestSchemaVersion();
        Assertions.assertThat(actualLatestSchemaVersion)
                .overridingErrorMessage("Expected Event to have latest schema version\n<%s> but it had\n<%s>",
                        expectedLatestSchemaVersion, actualLatestSchemaVersion)
                .isEqualTo(expectedLatestSchemaVersion);
        return myself;
    }

    public S supportSchemaVersion(final JsonSchemaVersion expectedSupportedSchemaVersion,
            final JsonSchemaVersion... furtherExpectedSupportedSchemaVersions) {
        isNotNull();
        final JsonSchemaVersion[] actualSupportedSchemaVersions = actual.getSupportedSchemaVersions();
        final JsonSchemaVersion[] allExpectedSupportedSchemaVersions =
                new JsonSchemaVersion[furtherExpectedSupportedSchemaVersions.length + 1];
        allExpectedSupportedSchemaVersions[0] = expectedSupportedSchemaVersion;
        System.arraycopy(furtherExpectedSupportedSchemaVersions, 0, allExpectedSupportedSchemaVersions, 1,
                furtherExpectedSupportedSchemaVersions.length);
        Assertions.assertThat(actualSupportedSchemaVersions)
                .overridingErrorMessage("Expected Event to support schema version(s)\n<%s> but it supported\n<%s>",
                        Arrays.toString(allExpectedSupportedSchemaVersions),
                        Arrays.toString(actualSupportedSchemaVersions))
                .contains(allExpectedSupportedSchemaVersions);
        return myself;
    }

    public S implementsSchemaVersion(final JsonSchemaVersion expectedImplementedSchemaVersion) {
        isNotNull();
        final Object actualImplementedSchemaVersion = actual.getImplementedSchemaVersion();
        Assertions.assertThat(actualImplementedSchemaVersion)
                .overridingErrorMessage("Expected Event to implement schema version\n<%s> but it implemented\n<%s>",
                        expectedImplementedSchemaVersion, actualImplementedSchemaVersion)
                .isEqualTo(expectedImplementedSchemaVersion);
        return myself;
    }

}
