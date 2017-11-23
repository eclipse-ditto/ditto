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
package org.eclipse.ditto.services.thingsearch.persistence.read;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Abstract base class for search persistence tests parameterized with version.
 */
@RunWith(Parameterized.class)
public abstract class AbstractVersionedThingSearchPersistenceTestBase extends AbstractReadPersistenceTestBase {

    @Parameterized.Parameters(name = "v{0}")
    public static List<JsonSchemaVersion> apiVersions() {
        return Arrays.asList(JsonSchemaVersion.values());
    }

    @Parameterized.Parameter
    public JsonSchemaVersion testedApiVersion;

    /** */
    @Before
    public void before() {
        super.before();
        if (isV2()) {
            createTestDataV2();
        } else {
            createTestDataV1();
        }
    }

    abstract void createTestDataV1();

    abstract void createTestDataV2();

    @Override
    JsonSchemaVersion getVersion() {
        return testedApiVersion;
    }

}
