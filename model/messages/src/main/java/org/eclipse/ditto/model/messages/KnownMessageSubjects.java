/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.messages;

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
