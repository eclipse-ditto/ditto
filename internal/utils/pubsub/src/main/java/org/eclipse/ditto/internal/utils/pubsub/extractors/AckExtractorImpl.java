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
package org.eclipse.ditto.internal.utils.pubsub.extractors;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Package-private implementation of {@link AckExtractor}.
 *
 * @param <T> type of messages.
 */
final class AckExtractorImpl<T> implements AckExtractor<T> {

    static final List<AcknowledgementLabel> BUILT_IN_LABELS = List.of(DittoAcknowledgementLabel.values());

    private final Function<T, EntityId> getEntityId;
    private final Function<T, DittoHeaders> getDittoHeaders;

    AckExtractorImpl(final Function<T, EntityId> getEntityId,
            final Function<T, DittoHeaders> getDittoHeaders) {
        this.getEntityId = getEntityId;
        this.getDittoHeaders = getDittoHeaders;
    }

    @Override
    public Set<AcknowledgementRequest> getAckRequests(final T message) {
        return getDittoHeaders.apply(message).getAcknowledgementRequests();
    }

    @Override
    public EntityId getEntityId(final T message) {
        return getEntityId.apply(message);
    }

    @Override
    public DittoHeaders getDittoHeaders(final T message) {
        return getDittoHeaders.apply(message);
    }
}
