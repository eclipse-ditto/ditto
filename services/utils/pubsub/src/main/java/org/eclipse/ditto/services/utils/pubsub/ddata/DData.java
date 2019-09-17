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
package org.eclipse.ditto.services.utils.pubsub.ddata;

/**
 * A package of ddata reader, writer, and creator of local subscriptions to plug into a pub-sub framework.
 *
 * @param <R> type of reads from the distributed data.
 * @param <W> type of writes from the distributed data.
 */
public interface DData<R, W> {

    /**
     * @return the distributed data reader.
     */
    DDataReader<R> getReader();

    /**
     * @return the distributed data writer.
     */
    DDataWriter<W> getWriter();

    /**
     * @return a new, empty subscriptions object.
     */
    Subscriptions<W> createSubscriptions();

}
