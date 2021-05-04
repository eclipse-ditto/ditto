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

import java.text.MessageFormat;

/**
 * Thrown to indicate that a HTTP status code is out of range, i.e. not within {@code 100} - {@code 599}.
 *
 * @since 2.0.0
 */
public final class HttpStatusCodeOutOfRangeException extends Exception {

    private static final long serialVersionUID = 8920002289261171460L;

    private static final String MSG_PATTERN = "Provided HTTP status code <{0}> is not within the range of 100 to 599.";

    /**
     * Constructs a new HttpStatusCodeOutOfRangeException.
     *
     * @param statusCode the invalid status code.
     */
    public HttpStatusCodeOutOfRangeException(final int statusCode) {
        super(MessageFormat.format(MSG_PATTERN, statusCode));
    }

}
