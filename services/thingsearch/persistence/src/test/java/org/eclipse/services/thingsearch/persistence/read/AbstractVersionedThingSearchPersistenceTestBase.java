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
package org.eclipse.services.thingsearch.persistence.read;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.services.thingsearch.persistence.read.document.ThingDocumentBuilder;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Abstract base class for search persistence tests parameterized with version.
 */
@RunWith(Parameterized.class)
public abstract class AbstractVersionedThingSearchPersistenceTestBase extends AbstractReadPersistenceTestBase {

    private static List<JsonSchemaVersion> ALL_API_VERSIONS = Arrays.asList(JsonSchemaVersion.values());

    @Parameterized.Parameter
    public JsonSchemaVersion testedApiVersion;

    /** */
    @Before
    public void before() {
       super.before();
       if (testedApiVersion == JsonSchemaVersion.V_1) {
           createTestDataV1();
       } else if (testedApiVersion == JsonSchemaVersion.V_2) {
           createTestDataV2();
       } else {
           throw new IllegalStateException("Unknown version: " + testedApiVersion);
       }
    }

    abstract void createTestDataV1();

    abstract void createTestDataV2();

    @Parameterized.Parameters(name = "v{0}")
    public static List<JsonSchemaVersion> apiVersions() {
        return ALL_API_VERSIONS;
    }

    ThingDocumentBuilder buildDocWithAclOrPolicy(final String thingId) {
        final ThingDocumentBuilder builder;
        if (testedApiVersion == JsonSchemaVersion.V_1) {
            builder = buildDocV1WithAcl(thingId);
        } else if (testedApiVersion == JsonSchemaVersion.V_2) {
            builder = buildDocV2WithGlobalReads(thingId);
        } else {
            throw new IllegalStateException();
        }

        return builder;
    }
}
