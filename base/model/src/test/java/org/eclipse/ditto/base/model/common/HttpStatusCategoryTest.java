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

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.common.HttpStatus.Category}.
 */
@RunWith(Parameterized.class)
public final class HttpStatusCategoryTest {

    @Parameterized.Parameter(0)
    public int statusCode;

    @Parameterized.Parameter(1)
    public HttpStatus.Category expectedCategory;

    @Parameterized.Parameters(name = "Status code {0} evaluates to category {1}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
                {Integer.MIN_VALUE, null},
                {0, null},
                {1, null},
                {99, null},
                {100, HttpStatus.Category.INFORMATIONAL},
                {199, HttpStatus.Category.INFORMATIONAL},
                {200, HttpStatus.Category.SUCCESS},
                {299, HttpStatus.Category.SUCCESS},
                {300, HttpStatus.Category.REDIRECTION},
                {399, HttpStatus.Category.REDIRECTION},
                {400, HttpStatus.Category.CLIENT_ERROR},
                {499, HttpStatus.Category.CLIENT_ERROR},
                {500, HttpStatus.Category.SERVER_ERROR},
                {599, HttpStatus.Category.SERVER_ERROR},
                {600, null},
                {601, null},
                {Integer.MAX_VALUE, null},
        });
    }

    @Test
    public void statusCodeMapsToExpectedCategory() {
        final Optional<HttpStatus.Category> actualCategory = HttpStatus.Category.of(statusCode);

        if (null == expectedCategory) {
            assertThat(actualCategory).isEmpty();
        } else {
            assertThat(actualCategory).hasValue(expectedCategory);
        }
    }

}
