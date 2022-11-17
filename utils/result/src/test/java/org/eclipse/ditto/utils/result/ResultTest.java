/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.utils.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.Test;

/**
 * Unit test for {@link Result}.
 */
public final class ResultTest {

    @Test
    public void tryToApplyWithNullSupplyingFunctionThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> Result.tryToApply(null))
                .withMessage("supplyingFunction")
                .withNoCause();
    }

    @Test
    public void tryToApplyReturnsOkWithExpectedValueIfSupplyingFunctionWasSuccessful() {
        final var successValue = "Hello World!";

        final var result = Result.tryToApply(() -> successValue);

        assertThat(result).isEqualTo(Result.ok(successValue));
    }

    @Test
    public void tryToApplyReturnsErrWithExpectedThrowableIfSupplyingFunctionFailed() {
        final var illegalStateException = new IllegalStateException("Yolo!");

        final var result = Result.tryToApply(() -> {
            throw illegalStateException;
        });

        assertThat(result).isEqualTo(Result.err(illegalStateException));
    }

}