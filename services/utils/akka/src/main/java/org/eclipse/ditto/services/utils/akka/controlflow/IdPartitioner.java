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
package org.eclipse.ditto.services.utils.akka.controlflow;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.signals.base.WithId;

import akka.japi.function.Function;

/**
 * Determines the partition number for a given message which implements {@link WithId}.
 * That means that e. g. each Thing ID gets its own partition, so messages to that Thing are sequentially processed
 * and thus the order is maintained.
 *
 * @param <T> the type of the message to get the partition number for.
 */
@Immutable
final class IdPartitioner<T> implements Function<T, Integer> {

    private static final long serialVersionUID = -7938270762761873804L;

    private final String specialEnforcementLaneHeaderKey;
    private final int parallelism;

    private IdPartitioner(final String specialEnforcementLaneHeaderKey, final int parallelism) {
        this.specialEnforcementLaneHeaderKey = specialEnforcementLaneHeaderKey;
        this.parallelism = parallelism;
    }

    /**
     * Returns an instance of {@code IdPartitioner}.
     *
     * @param specialEnforcementLaneHeaderKey for {@code signals} marked with a DittoHeader with that key, the
     * "special enforcement lane" shall be used &ndash; meaning that those messages are processed not based on the hash
     * of their ID but in a common "special lane".
     * @param parallelism the parallelism to use (how many partitions to process in parallel) - which should be based
     * on the amount of available CPUs.
     * @return the instance.
     * @throws NullPointerException if {@code specialEnforcementLaneHeaderKey} is {@code null}.
     * @throws IllegalArgumentException if {@code specialEnforcementLaneHeaderKey} is empty.
     * @param <T> the type of the message to get the partition number for.
     */
    public static <T> IdPartitioner<T> of(final String specialEnforcementLaneHeaderKey, final int parallelism) {
        argumentNotEmpty(specialEnforcementLaneHeaderKey, "specialEnforcementLaneHeaderKey");
        return new IdPartitioner<>(specialEnforcementLaneHeaderKey, parallelism);
    }

    @Override
    public Integer apply(final T message) {
        final int result;
        if (isForSpecialLane(checkNotNull(message, "message"))) {
            result = 0; // 0 is a special "lane" which is required in some special cases
        } else if (message instanceof WithId) {
            final int hashCode = getAppropriateHashCode((WithId) message);
            result = Math.abs(hashCode % parallelism) + 1;
        } else {
            result = 0;
        }
        return result;
    }

    /**
     * Checks whether a special lane is required for the passed {@code msg}. This is for example required when during
     * an enforcement another call to the enforcer is done, the hash of the 2 messages might collide and block
     * each other.
     *
     * @param msg the message to check for whether to use the special lane.
     * @return whether to use the special lane or not.
     */
    private boolean isForSpecialLane(final Object msg) {
        if (msg instanceof WithDittoHeaders) {
            final DittoHeaders dittoHeaders = ((WithDittoHeaders<?>) msg).getDittoHeaders();
            return dittoHeaders.containsKey(specialEnforcementLaneHeaderKey);
        }
        return false;
    }

    private static int getAppropriateHashCode(final WithId message) {
        final EntityId id = message.getEntityId();
        if (!id.isDummy()) {
            return id.hashCode();
        }

        // e. g. the case for RetrieveThings command - in that case it is important that not all
        // RetrieveThings message are processed in the same "lane", so use msg hash instead:
        return message.hashCode();
    }

}
