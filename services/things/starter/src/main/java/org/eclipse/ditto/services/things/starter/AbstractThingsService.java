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
package org.eclipse.ditto.services.things.starter;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.services.base.BaseConfigKey;
import org.eclipse.ditto.services.base.BaseConfigKeys;
import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.base.StatsdMongoDbMetricsStarter;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.services.things.starter.util.ConfigKeys;
import org.slf4j.Logger;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.ActorMaterializer;

/**
 * Abstract base implementation for starting Things service with configurable actors.
 * <ul>
 * <li>Reads configuration, enhances it with cloud environment settings.</li>
 * <li>Sets up Akka actor system.</li>
 * <li>Wires up Akka HTTP Routes.</li>
 * </ul>
 */
public abstract class AbstractThingsService extends DittoService {

    /**
     * Name for the Akka Actor System of the Things service.
     */
    private static final String SERVICE_NAME = "things";

    private static final BaseConfigKeys CONFIG_KEYS = BaseConfigKeys.getBuilder()
            .put(BaseConfigKey.Cluster.MAJORITY_CHECK_ENABLED, ConfigKeys.Cluster.MAJORITY_CHECK_ENABLED)
            .put(BaseConfigKey.Cluster.MAJORITY_CHECK_DELAY, ConfigKeys.Cluster.MAJORITY_CHECK_DELAY)
            .put(BaseConfigKey.StatsD.HOSTNAME, ConfigKeys.StatsD.HOSTNAME)
            .put(BaseConfigKey.StatsD.PORT, ConfigKeys.StatsD.PORT)
            .build();

    private final Logger logger;
    private final ThingSnapshotter.Create thingSnapshotterCreate;

    /**
     * Constructs a new {@code AbstractThingsService} object.
     *
     * @param logger the logger to be used.
     * @param thingSnapshotterCreate functional interface for the constructor of snapshotter classes.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected AbstractThingsService(final Logger logger, final ThingSnapshotter.Create thingSnapshotterCreate) {
        super(logger, SERVICE_NAME, ThingsRootActor.ACTOR_NAME, CONFIG_KEYS);

        this.logger = logger;
        this.thingSnapshotterCreate = checkNotNull(thingSnapshotterCreate);
    }

    @Override
    protected void startStatsdMetricsReporter(final ActorSystem actorSystem, final Config config) {
        StatsdMongoDbMetricsStarter.newInstance(config, CONFIG_KEYS, actorSystem, SERVICE_NAME, logger).run();
    }

    @Override
    protected Props getMainRootActorProps(final Config config, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return ThingsRootActor.props(config, pubSubMediator, materializer,
                ThingSupervisorActorPropsFactory.getInstance(config, pubSubMediator, thingSnapshotterCreate));
    }

}
