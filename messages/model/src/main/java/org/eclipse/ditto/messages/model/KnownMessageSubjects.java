/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.messages.model;

import javax.annotation.concurrent.Immutable;

/**
 * Contains constants with "known" Ditto Message subjects - those have a special meaning in Ditto and must not be used
 * for other Messages.
 */
@Immutable
public final class KnownMessageSubjects {

    /**
     * The Message subject of "Claim" Message.
     */
    public static final String CLAIM_SUBJECT = "claim";

    private KnownMessageSubjects() {
        throw new AssertionError();
    }
}
