/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.signals.commands;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonPointer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests {@link ThingResource}.
 */
class ThingResourceTest {

    @ParameterizedTest
    @EnumSource(ThingResourceTestCase.class)
    void testFromValidPointer(final ThingResourceTestCase testCase) {
        assertThat(ThingResource.from(testCase.getPath())).contains(testCase.getExpectedResource());
    }

    @Test
    void testUnknownPointer() {
        assertThat(ThingResource.from(JsonPointer.of("test"))).isEmpty();
    }
}
