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
package org.eclipse.ditto.services.models.thingsearch.query.filter;

import static org.mockito.Mockito.RETURNS_DEFAULTS;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

class AnswerWithSelf implements Answer<Object> {

    // CS:OFF Justification: Suppress "Throwing Throwable is not allowed"
    @Override
    public Object answer(final InvocationOnMock invocationOnMock) throws Throwable
    // CS:ON
    {
        final Object mock = invocationOnMock.getMock();
        if (invocationOnMock.getMethod().getReturnType().isInstance(mock)) {
            return mock;
        }

        return RETURNS_DEFAULTS.answer(invocationOnMock);
    }
}
