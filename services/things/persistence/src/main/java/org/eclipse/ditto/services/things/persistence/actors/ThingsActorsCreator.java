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
package org.eclipse.ditto.services.things.persistence.actors;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Interface for creating actors in Things service.
 */
public interface ThingsActorsCreator {

    /**
     * Creates actor configuration of the root actor of Things service.
     *
     * @return Actor configuration of the root actor.
     */
    Props createRootActor();

    /**
     * Creates actor configuration of the supervisors of persistent actors of Things.
     *
     * @param pubSubMediator Reference of the pub-sub mediator.
     * @param thingCacheFacade Reference of the cache facade actor.
     * @return Actor configuration of the supervisor actors.
     */
    Props createSupervisorActor(final ActorRef pubSubMediator, final ActorRef thingCacheFacade);

    /**
     * Creates actor configuration of the persistent actor of a Thing.
     *
     * @param thingId ID of the Thing.
     * @param pubSubMediator Reference of the pub-sub mediator.
     * @param thingCacheFacade Reference of the cache facade actor.
     * @return Actor configuration of the persistent actor.
     */
    Props createPersistentActor(final String thingId, final ActorRef pubSubMediator, final ActorRef thingCacheFacade);
}
