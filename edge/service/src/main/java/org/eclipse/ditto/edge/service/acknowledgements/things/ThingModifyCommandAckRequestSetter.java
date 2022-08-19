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
package org.eclipse.ditto.edge.service.acknowledgements.things;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.acks.AbstractCommandAckRequestSetter;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;

/**
 * This UnaryOperator accepts a ThingModifyCommand and checks whether its DittoHeaders should be extended by an
 * {@link org.eclipse.ditto.base.model.acks.AcknowledgementRequest} for {@link org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel#TWIN_PERSISTED}.
 * <p>
 * If so, the result is a new command with extended headers, else the same command is returned.
 * </p>
 *
 * @since 1.1.0
 */
@Immutable
public final class ThingModifyCommandAckRequestSetter extends AbstractCommandAckRequestSetter<ThingModifyCommand<?>> {

    private static final ThingModifyCommandAckRequestSetter INSTANCE = new ThingModifyCommandAckRequestSetter();

    private ThingModifyCommandAckRequestSetter() {
        // The Ditto acknowledgement label "search-persisted" is tolerated but not set by default.
        super(DittoAcknowledgementLabel.TWIN_PERSISTED, Collections.singleton(DittoAcknowledgementLabel.LIVE_RESPONSE));
    }

    /**
     * Returns an instance of {@code ThingModifyCommandAckRequestSetter}.
     *
     * @return the instance.
     */
    public static ThingModifyCommandAckRequestSetter getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isApplicable(final ThingModifyCommand<?> command) {
        checkNotNull(command, "command");
        return !isLiveChannelCommand(command);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
    public Class<ThingModifyCommand<?>> getMatchedClass() {
        return (Class) ThingModifyCommand.class;
    }

    @Override
    protected boolean isBindResponseRequiredToAddingRemovingImplicitLabel() {
        return false;
    }
}
