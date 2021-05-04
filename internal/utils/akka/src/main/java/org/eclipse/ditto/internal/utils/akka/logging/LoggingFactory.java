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
package org.eclipse.ditto.internal.utils.akka.logging;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * A factory for creating objects related to logging.
 * The main purpose of this class is to hide concrete implementations.
 *
 * @since 1.4.0
 */
@Immutable
final class LoggingFactory {

    private LoggingFactory() {
        throw new AssertionError();
    }

    static MdcEntry newMdcEntry(final CharSequence key, @Nullable final CharSequence value) {
        return DefaultMdcEntry.of(key, value);
    }

}
