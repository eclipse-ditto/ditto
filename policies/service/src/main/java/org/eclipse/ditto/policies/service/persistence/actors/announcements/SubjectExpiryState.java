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
package org.eclipse.ditto.policies.service.persistence.actors.announcements;

/**
 * State of subject expiry actor.
 */
enum SubjectExpiryState {

    /**
     * Waiting to send an announcement in the future.
     */
    TO_ANNOUNCE,

    /**
     * Waiting for acknowledgement from either {@code AcknowledgementAggregatorActor} if there are requested-acks,
     * or self if there are no requested-acks.
     */
    TO_ACKNOWLEDGE,

    /**
     * Waiting to delete the subject in the future.
     */
    TO_DELETE,

    /**
     * Waiting for supervisor to acknowledge deletion of this subject.
     */
    DELETED
}
