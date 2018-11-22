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
package org.eclipse.ditto.model.enforcers;



import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;


public class JsonObjectMergerTest {

    @Test
    public void apply() {
        final JsonObjectMerger underTest = new JsonObjectMerger();
        final JsonObject mergedObject = underTest.apply(JsonFactory.nullObject(), JsonFactory.nullObject());

        assertThat(mergedObject).isEqualTo(JsonFactory.nullObject());
    }
}