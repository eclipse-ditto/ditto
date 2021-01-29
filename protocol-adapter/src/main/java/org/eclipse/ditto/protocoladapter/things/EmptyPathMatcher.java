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
package org.eclipse.ditto.protocoladapter.things;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocoladapter.PayloadPathMatcher;
import org.eclipse.ditto.protocoladapter.UnknownPathException;

/**
 * PayloadPathMatcher implementation for adapters that do not rely on the path to resolve the type from the message.
 * <p>
 * TODO adapt @since annotation @since 1.6.0
 */
final class EmptyPathMatcher implements PayloadPathMatcher {

    private static final EmptyPathMatcher INSTANCE = new EmptyPathMatcher();

    private EmptyPathMatcher() {
    }

    static EmptyPathMatcher getInstance() {
        return INSTANCE;
    }

    @Override
    public String match(final JsonPointer path) {
        throw UnknownPathException.newBuilder(path).build();
    }
}
