/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
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
