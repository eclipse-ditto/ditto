/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

/**
 * Interface providing methods for the different outcomes of a mapping - dropped, error and success. As a message may
 * either be the input for multiple mappings or a single mapping my produce multiple results, the defined methods may
 * be called multiple times for a single processed message. Multiple mappers are independent, the result of mapper A
 * does not influence the result of mapper B. E.g. mapper A fails but mapper B is successful is a valid case.
 *
 * @param <T> the type parameter for successfully mapped messages
 */
interface MappingResultHandler<T> {

    /**
     * Is called when the mapping was successful.
     *
     * @param mappedMessage the successfully mapped message
     */
    void onMessageMapped(T mappedMessage);

    /**
     * Is called when the mapping produced no result i.e. message should be dropped.
     */
    void onMessageDropped();

    /**
     * Is called when the mapping failed.
     *
     * @param ex the exception that was thrown by the mapper
     */
    void onException(Exception ex);
}