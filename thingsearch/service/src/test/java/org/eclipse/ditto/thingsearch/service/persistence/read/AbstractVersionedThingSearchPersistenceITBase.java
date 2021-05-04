/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.read;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.rql.query.Query;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Abstract base class for search persistence tests parameterized with version.
 */
@RunWith(Parameterized.class)
public abstract class AbstractVersionedThingSearchPersistenceITBase extends AbstractReadPersistenceITBase {

    public static List<JsonSchemaVersion> apiVersions() {
        return Arrays.asList(JsonSchemaVersion.values());
    }

    @Parameterized.Parameters(name = "v{0} - {1}")
    public static List<Object[]> versionAndQueryClassParameters() {
        return apiVersions()
                .stream()
                .map(apiVersion -> new Object[]{apiVersion, Query.class.getSimpleName()})
                .collect(Collectors.toList());
    }

    @Parameterized.Parameter
    public JsonSchemaVersion testedApiVersion;

    @Parameterized.Parameter(1)
    public String queryClass;


    @Before
    public void before() {
        super.before();
        createTestDataV2();
    }

    abstract void createTestDataV2();

}
