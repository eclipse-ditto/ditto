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
package org.eclipse.ditto.base.model.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.common.HttpStatusCodeOutOfRangeExceptionTest}.
 */
public final class HttpStatusCodeOutOfRangeExceptionTest {

    @Test
    public void messageFollowsExpectedPattern() {
        final int statusCode = 0;

        final HttpStatusCodeOutOfRangeException underTest = new HttpStatusCodeOutOfRangeException(statusCode);

        assertThat(underTest.getMessage()).isEqualTo(
                String.format("Provided HTTP status code <%d> is not within the range of 100 to 599.", statusCode));
    }

}
