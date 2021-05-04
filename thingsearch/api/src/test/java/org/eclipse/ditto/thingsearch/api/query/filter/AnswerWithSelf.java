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
package org.eclipse.ditto.thingsearch.api.query.filter;

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
