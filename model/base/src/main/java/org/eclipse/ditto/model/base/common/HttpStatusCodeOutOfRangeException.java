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
package org.eclipse.ditto.model.base.common;

import java.text.MessageFormat;

/**
 * Thrown to indicate that a HTTP status code is out of range, i.e. not within {@code 100} - {@code 599}.
 */
public class HttpStatusCodeOutOfRangeException extends Exception {

    private static final long serialVersionUID = 8920002289261171460L;
    
    private static final String MSG_PATTERN = "<{0}> is not within the range of valid HTTP status codes (100 - 599)!";
    
    public HttpStatusCodeOutOfRangeException(final int statusCode) {
        super(MessageFormat.format(MSG_PATTERN, statusCode));
    }

}
