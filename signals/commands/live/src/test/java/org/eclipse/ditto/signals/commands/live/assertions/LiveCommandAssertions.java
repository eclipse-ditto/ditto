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
package org.eclipse.ditto.signals.commands.live.assertions;

import org.eclipse.ditto.signals.commands.base.assertions.CommandAssertions;
import org.eclipse.ditto.signals.commands.live.base.LiveCommand;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;

/**
 * Custom test assertions for {@link LiveCommand}s.
 */
public class LiveCommandAssertions extends CommandAssertions {

    public static LiveCommandAssert assertThat(final LiveCommand<?, ?> liveCommand) {
        return new LiveCommandAssert(liveCommand);
    }

    /**
     * Returns an Assert for checking the given {@link LiveCommandAnswer}.
     *
     * @param liveCommandAnswer the live command answer to be checked.
     * @return the Assert.
     */
    public static LiveCommandAnswerAssert assertThat(final LiveCommandAnswer liveCommandAnswer) {
        return new LiveCommandAnswerAssert(liveCommandAnswer);
    }

}
