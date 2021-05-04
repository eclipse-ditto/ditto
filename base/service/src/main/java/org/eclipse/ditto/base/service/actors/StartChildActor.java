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
package org.eclipse.ditto.base.service.actors;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import akka.actor.Props;

/**
 * Message that holds props of an actor and the name to start this actor with.
 * Send this Object to an actor (e.g. {@link DittoRootActor}) in order to start
 * an actor in the actor context of this actor.
 * <p>
 * This helper message is <b>NOT serializable</b> in any way, so only use it inside a local ActorSystem.
 * </p>
 */
@Immutable
public final class StartChildActor {

    private final Props props;
    private final String actorName;

    /**
     * Constructs a new instance of StartChildActor
     *
     * @param props the props of the child actor.
     * @param actorName the name the child actor should be started with.
     */
    public StartChildActor(final Props props, final String actorName) {
        this.props = props;
        this.actorName = actorName;
    }

    public Props getProps() {
        return props;
    }

    public String getActorName() {
        return actorName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StartChildActor that = (StartChildActor) o;
        return props.equals(that.props) &&
                actorName.equals(that.actorName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(props, actorName);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "props=" + props +
                ", actorName=" + actorName +
                "]";
    }

}
