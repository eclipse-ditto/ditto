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
package org.eclipse.ditto.services.utils.devops;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.devops.LoggerConfig;
import org.eclipse.ditto.model.devops.LoggingFacade;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.devops.AggregatedDevOpsCommandResponse;
import org.eclipse.ditto.signals.commands.devops.ChangeLogLevel;
import org.eclipse.ditto.signals.commands.devops.ChangeLogLevelResponse;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommand;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandResponse;
import org.eclipse.ditto.signals.commands.devops.RetrieveLoggerConfig;
import org.eclipse.ditto.signals.commands.devops.RetrieveLoggerConfigResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.Duration;

/**
 * An actor to consume {@link org.eclipse.ditto.signals.commands.devops.DevOpsCommand}s and reply appropriately.
 */
public final class DevOpsCommandsActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "devOpsCommandsActor";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final LoggingFacade loggingFacade;

    private final String serviceName;
    private final Integer instance;
    private final ActorRef pubSubMediator;

    private DevOpsCommandsActor(final LoggingFacade loggingFacade, final String serviceName, final Integer instance) {
        this.loggingFacade = loggingFacade;
        this.serviceName = serviceName;
        this.instance = instance;

        pubSubMediator = DistributedPubSub.get(getContext().system()).mediator();
        getContext().actorOf(
                PubSubSubscriberActor.props(pubSubMediator, serviceName, instance,
                        RetrieveLoggerConfig.TYPE,
                        ChangeLogLevel.TYPE
                ),
                "pubSubSubscriber");
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param loggingFacade a facade providing logging functionality.
     * @return the Akka configuration Props object.
     */
    public static Props props(final LoggingFacade loggingFacade, final String serviceName, final Integer instance) {
        return Props.create(DevOpsCommandsActor.class,
                (Creator<DevOpsCommandsActor>) () -> new DevOpsCommandsActor(loggingFacade, serviceName, instance));
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ChangeLogLevel.class, this::handleInitialDevOpsCommand)
                .match(RetrieveLoggerConfig.class, this::handleInitialDevOpsCommand)
                .match(DevOpsCommandViaPubSub.class, this::handleDevOpsCommandViaPubSub)
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    /**
     * DevOps commands issued via the HTTP DevopsRoute are handled here on the (gateway) cluster node which got the HTTP
     * request. The job now is to: <ul> <li>publish the command in the cluster (so that all services which should react
     * on that command get it)</li> <li>start aggregation of responses</li> </ul>
     *
     * @param command the initial DevOpsCommand to handle
     */
    private void handleInitialDevOpsCommand(final DevOpsCommand<?> command) {

        final ActorRef responseCorrelationActor = getContext().actorOf(
                DevOpsCommandResponseCorrelationActor.props(getSender(), command),
                command.getDittoHeaders().getCorrelationId()
                        .orElseThrow(() -> new IllegalArgumentException("Missing correlation-id for DevOpsCommand")));

        final String topic;
        if (command.getServiceName().isPresent()) {
            final String serviceName = command.getServiceName().get();
            if (command.getInstance().isPresent()) {
                topic = command.getType() + ":" + serviceName + ":" + command.getInstance().get();
            } else {
                topic = command.getType() + ":" + serviceName;
            }
        } else {
            topic = command.getType();
        }

        log.info("Publishing DevOpsCommand <{}> into cluster on topic <{}>", command.getType(), topic);
        pubSubMediator.tell(new DistributedPubSubMediator.Publish(topic, command), responseCorrelationActor);
    }

    private void handleDevOpsCommandViaPubSub(final DevOpsCommandViaPubSub devOpsCommandViaPubSub) {

        final DevOpsCommand wrappedCommand = devOpsCommandViaPubSub.wrappedCommand;
        // TODO TJ how to get rid of this if-else?
        if (wrappedCommand instanceof ChangeLogLevel) {
            handleDevOpsCommand((ChangeLogLevel) wrappedCommand);
        } else if (wrappedCommand instanceof RetrieveLoggerConfig) {
            handleDevOpsCommand((RetrieveLoggerConfig) wrappedCommand);
        }
    }

    private void handleDevOpsCommand(final ChangeLogLevel command) {

        final Boolean isApplied = loggingFacade.setLogLevel(command.getLoggerConfig());
        final ChangeLogLevelResponse changeLogLevelResponse =
                ChangeLogLevelResponse.of(serviceName, instance, isApplied, command.getDittoHeaders());
        getSender().tell(changeLogLevelResponse, getSelf());
    }

    private void handleDevOpsCommand(final RetrieveLoggerConfig command) {

        final List<LoggerConfig> loggerConfigs;

        if (command.isAllKnownLoggers()) {
            loggerConfigs = loggingFacade.getLoggerConfig();
        } else {
            loggerConfigs = loggingFacade.getLoggerConfig(command.getSpecificLoggers());
        }

        final RetrieveLoggerConfigResponse retrieveLoggerConfigResponse =
                RetrieveLoggerConfigResponse.of(serviceName, instance, loggerConfigs, command.getDittoHeaders());
        getSender().tell(retrieveLoggerConfigResponse, getSelf());
    }


    /**
     * Child actor handling the distributed pub/sub subscriptions of DevOpsCommands in the cluster.
     */
    private static final class PubSubSubscriberActor extends AbstractActor {

        private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

        private PubSubSubscriberActor(final ActorRef pubSubMediator, final String serviceName, final Integer instance,
                final String... pubSubTopicsToSubscribeTo) {

            Arrays.stream(pubSubTopicsToSubscribeTo).forEach(topic ->
                    subscribeToDevopsTopic(pubSubMediator, topic, serviceName, instance));
        }

        /**
         * @return the Akka configuration Props object.
         */
        static Props props(final ActorRef pubSubMediator, final String serviceName, final Integer instance,
                final String... pubSubTopicsToSubscribeTo) {
            return Props.create(PubSubSubscriberActor.class,
                    (Creator<PubSubSubscriberActor>) () -> new PubSubSubscriberActor(pubSubMediator, serviceName,
                            instance, pubSubTopicsToSubscribeTo));
        }

        private void subscribeToDevopsTopic(final ActorRef pubSubMediator, final String topic,
                final String serviceName, final Integer instance) {
            pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(topic, getSelf()), getSelf());
            pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(
                    String.join(":", topic, serviceName), getSelf()), getSelf());
            pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(
                    String.join(":", topic, serviceName, instance.toString()), getSelf()), getSelf());
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(DevOpsCommand.class, command -> getContext().getParent().forward(
                            new DevOpsCommandViaPubSub(command), getContext()
                    ))
                    .match(DistributedPubSubMediator.SubscribeAck.class, this::handleSubscribeAck)
                    .matchAny(m -> {
                        log.warning("Unknown message: {}", m);
                        unhandled(m);
                    }).build();
        }

        private void handleSubscribeAck(final DistributedPubSubMediator.SubscribeAck subscribeAck) {
            log.info("Successfully subscribed to distributed pub/sub on topic '{}'", subscribeAck.subscribe().topic());
        }
    }

    private static final class DevOpsCommandViaPubSub {

        private final DevOpsCommand wrappedCommand;

        private DevOpsCommandViaPubSub(final DevOpsCommand wrappedCommand) {this.wrappedCommand = wrappedCommand;}
    }

    /**
     * Child actor correlating the {@link DevOpsCommandResponse}s
     */
    private static final class DevOpsCommandResponseCorrelationActor extends AbstractActor {


        /**
         * @return the Akka configuration Props object.
         */
        static Props props(final ActorRef devOpsCommandSender, final DevOpsCommand<?> devOpsCommand) {
            return Props.create(DevOpsCommandResponseCorrelationActor.class,
                    (Creator<DevOpsCommandResponseCorrelationActor>) () ->
                            new DevOpsCommandResponseCorrelationActor(devOpsCommandSender, devOpsCommand));
        }

        private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

        private final ActorRef devOpsCommandSender;
        private final DevOpsCommand<?> devOpsCommand;
        private final List<DevOpsCommandResponse<?>> commandResponses = new ArrayList<>();

        private DevOpsCommandResponseCorrelationActor(final ActorRef devOpsCommandSender,
                final DevOpsCommand<?> devOpsCommand) {

            this.devOpsCommandSender = devOpsCommandSender;
            this.devOpsCommand = devOpsCommand;
            getContext().setReceiveTimeout(Duration.create(200, TimeUnit.MILLISECONDS)); // TODO TJ configurable via header
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(DevOpsCommandResponse.class, this::handleDevOpsCommandResponse)
                    .match(ReceiveTimeout.class, receiveTimeout -> {
                        log.info(
                                "Got ReceiveTimeout, answering with all aggregated DevOpsCommandResponses and stopping ourself..");
                        devOpsCommandSender.tell(AggregatedDevOpsCommandResponse.of(commandResponses,
                                DevOpsCommandResponse.TYPE_PREFIX + devOpsCommand.getName(),
                                devOpsCommand.getDittoHeaders()),
                                getSelf());
                        getContext().stop(getSelf());
                    })
                    .matchAny(m -> {
                        log.warning("Unknown message: {}", m);
                        unhandled(m);
                    }).build();
        }

        private void handleDevOpsCommandResponse(final DevOpsCommandResponse<?> devOpsCommandResponse) {
            log.debug("Received DevOpsCommandResponse from service/instance <{}/{}>",
                    devOpsCommandResponse.getServiceName().orElse("empty"),
                    devOpsCommandResponse.getInstance().orElse(-1));
            commandResponses.add(devOpsCommandResponse);
        }
    }

}
