/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

public final class BaseClientStateTest {

    @Test
    public void toJsonFromJsonResultsInEqualObject() {
        Arrays.stream(BaseClientState.values()).forEach(initialState -> {
            final JsonObject jsonState = initialState.toJson();
            final BaseClientState parsedState = BaseClientState.fromJson(jsonState);
            assertThat(parsedState).isEqualTo(initialState);
        });
    }

}
