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
package org.eclipse.ditto.services.connectivity.messaging;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.HttpStatusCode;

/**
 * Default implementation of {@link ExceptionToAcknowledgementConverter} which uses
 * {@link HttpStatusCode#INTERNAL_SERVER_ERROR} as status code for generic exceptions.
 *
 * @since 1.3.0
 */
@Immutable
final class DefaultExceptionToAcknowledgementConverter extends ExceptionToAcknowledgementConverter {

    private static DefaultExceptionToAcknowledgementConverter instance = null;

    private DefaultExceptionToAcknowledgementConverter() {
        super();
    }

    /**
     * Returns an instance of DefaultExceptionToAcknowledgementConverter.
     *
     * @return the instance.
     */
    static DefaultExceptionToAcknowledgementConverter getInstance() {
        DefaultExceptionToAcknowledgementConverter result = instance;
        if (null == result) {
            result = new DefaultExceptionToAcknowledgementConverter();
            instance = result;
        }
        return result;
    }

    @Override
    protected HttpStatusCode getStatusCodeForGenericException(final Exception exception) {
        return HttpStatusCode.INTERNAL_SERVER_ERROR;
    }

}
