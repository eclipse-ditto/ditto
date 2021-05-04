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
package org.eclipse.ditto.internal.utils.pubsub.ddata;

/**
 * A package of ddata reader, writer, and creator of local subscriptions to plug into a pub-sub framework.
 *
 * @param <K> type of keys of the distributed multimap.
 * @param <R> type of reads from the distributed data.
 * @param <W> type of writes from the distributed data.
 */
public interface DData<K, R, W extends DDataUpdate<?>> {

    /**
     * @return the distributed data reader.
     */
    DDataReader<K, R> getReader();

    /**
     * @return the distributed data writer.
     */
    DDataWriter<K, W> getWriter();

}
