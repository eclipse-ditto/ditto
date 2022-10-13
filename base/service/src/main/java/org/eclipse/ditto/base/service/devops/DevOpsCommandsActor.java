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
package org.eclipse.ditto.base.service.devops;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.api.devops.LoggerConfig;
import org.eclipse.ditto.base.api.devops.LoggingFacade;
import org.eclipse.ditto.base.api.devops.signals.commands.AggregatedDevOpsCommandResponse;
import org.eclipse.ditto.base.api.devops.signals.commands.ChangeLogLevel;
import org.eclipse.ditto.base.api.devops.signals.commands.ChangeLogLevelResponse;
import org.eclipse.ditto.base.api.devops.signals.commands.DevOpsCommand;
import org.eclipse.ditto.base.api.devops.signals.commands.DevOpsCommandResponse;
import org.eclipse.ditto.base.api.devops.signals.commands.DevOpsErrorResponse;
import org.eclipse.ditto.base.api.devops.signals.commands.ExecutePiggybackCommand;
import org.eclipse.ditto.base.api.devops.signals.commands.ExecutePiggybackCommandResponse;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveLoggerConfig;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveLoggerConfigResponse;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.AskException;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.JsonParsable;
import org.eclipse.ditto.base.model.signals.JsonTypeNotParsableException;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.utils.akka.actors.RetrieveConfigBehavior;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.cluster.JsonValueSourceRef;
import org.eclipse.ditto.internal.utils.cluster.MappingStrategies;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import com.typesafe.config.Config;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

/**
 * An actor to consume {@link org.eclipse.ditto.base.api.devops.signals.commands.DevOpsCommand}s and reply appropriately.
 */
public final class DevOpsCommandsActor extends AbstractActor implements RetrieveConfigBehavior {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "devOpsCommandsActor";

    /**
     * Ditto header to turn aggregation on and off.
     */
    public static final String AGGREGATE_HEADER = "aggregate";

    /**
     * Name of the poison pill "command".
     */
    private static final String POISON_PILL_NAME = "poison-pill";

    private static final Duration DEFAULT_RECEIVE_TIMEOUT = Duration.ofMillis(10_000);
    private static final String UNKNOWN_MESSAGE_TEMPLATE = "Unknown message: {}";
    private static final String TOPIC_HEADER = "topic";
    private static final String IS_GROUP_TOPIC_HEADER = "is-group-topic";

    private final ThreadSafeDittoLoggingAdapter logger = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);

    private final LoggingFacade loggingFacade;

    private final String serviceName;
    private final String instance;
    private final ActorRef pubSubMediator;
    private final MappingStrategies serviceMappingStrategy;
    private final Cluster cluster;

    @SuppressWarnings("unused")
    private DevOpsCommandsActor(final LoggingFacade loggingFacade, final String serviceName, final String instance) {
        this.loggingFacade = loggingFacade;
        this.serviceName = serviceName;
        this.instance = instance;

        final var context = getContext();
        pubSubMediator = DistributedPubSub.get(context.getSystem()).mediator();
        serviceMappingStrategy = MappingStrategies.loadMappingStrategies(context.getSystem());
        cluster = Cluster.get(context.getSystem());
        context.actorOf(PubSubSubscriberActor.props(pubSubMediator, serviceName, instance,
                        RetrieveLoggerConfig.TYPE,
                        ChangeLogLevel.TYPE,
                        ExecutePiggybackCommand.TYPE),
                "pubSubSubscriber");
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param loggingFacade a facade providing logging functionality.
     * @param serviceName name of the microservice.
     * @param instance instance number of the microservice instance.
     * @return the Akka configuration Props object.
     */
    public static Props props(final LoggingFacade loggingFacade, final String serviceName, final String instance) {
        return Props.create(DevOpsCommandsActor.class, loggingFacade, serviceName, instance);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(DevOpsCommand.class, this::handleInitialDevOpsCommand)
                .match(DevOpsCommandViaPubSub.class, this::handleDevOpsCommandViaPubSub)
                .build()
                .orElse(retrieveConfigBehavior())
                .orElse(matchAnyUnhandled());
    }

    private Receive matchAnyUnhandled() {
        return ReceiveBuilder.create()
                .matchAny(m -> {
                    logger.warning(UNKNOWN_MESSAGE_TEMPLATE, m);
                    unhandled(m);
                }).build();
    }

    @Override
    public Config getConfig() {
        return getContext().getSystem().settings().config();
    }

    /**
     * DevOps commands issued via the HTTP DevopsRoute are handled here on the (gateway) cluster node which got the HTTP
     * request. The job now is to:
     * <ul>
     * <li>publish the command in the cluster (so that all services which should react on that command get it)</li>
     * <li>start aggregation of responses</li>
     * </ul>
     *
     * @param command the initial DevOpsCommand to handle
     */
    private void handleInitialDevOpsCommand(final DevOpsCommand<?> command) {
        final var responseCorrelationActorSupplier = getResponseCorrelationActorSupplier(command);
        if (isExecutePiggybackCommandToPubSubMediator(command)) {
            executeAsPiggybackCommandToPubSubMediator(command, responseCorrelationActorSupplier);
        } else {
            executeAsIndirectPiggybackCommandToPubSubMediator(command, responseCorrelationActorSupplier);
        }
    }

    private int getExpectedResponses(final DevOpsCommand<?> command) {
        if (command.getServiceName().isPresent() && command.getInstance().isPresent()) {
            // response wanted from 1 instance only
            return 1;
        } else if (command.getServiceName().isEmpty()) {
            // response wanted from every cluster member
            return (int) StreamSupport.stream(cluster.state().getMembers().spliterator(), false).count();
        } else {
            // response wanted from all instances of a service
            final var theServiceName = command.getServiceName().orElseThrow();
            return (int) StreamSupport.stream(cluster.state().getMembers().spliterator(), false)
                    .filter(member -> member.getRoles().contains(theServiceName))
                    .count();
        }
    }

    private Supplier<ActorRef> getResponseCorrelationActorSupplier(final DevOpsCommand<?> command) {
        final int expectedResponses = getExpectedResponses(command);
        final var context = getContext();
        return () -> context.actorOf(
                DevOpsCommandResponseCorrelationActor.props(getSender(), command, expectedResponses),
                getCorrelationIdOrThrow(command));
    }

    private static String getCorrelationIdOrThrow(final WithDittoHeaders command) {
        final var dittoHeaders = command.getDittoHeaders();
        return dittoHeaders.getCorrelationId()
                .orElseThrow(() -> new IllegalArgumentException("Missing correlation-id for DevOpsCommand!"));
    }

    private boolean isExecutePiggybackCommandToPubSubMediator(final DevOpsCommand<?> command) {
        final boolean result;
        if (ExecutePiggybackCommand.TYPE.equals(command.getType())) {
            final var executePiggyback = (ExecutePiggybackCommand) command;
            final var pubSubMediatorPath = pubSubMediator.path();
            result = Objects.equals(executePiggyback.getTargetActorSelection(),
                    pubSubMediatorPath.toStringWithoutAddress());
        } else {
            result = false;
        }
        return result;
    }

    private void executeAsPiggybackCommandToPubSubMediator(final DevOpsCommand<?> command,
            final Supplier<ActorRef> responseCorrelationActor) {

        tryInterpretAsDirectPublication(command, publish -> {
            logger.withCorrelationId(command)
                    .info("Publishing <{}> into cluster on topic <{}> with sendOneMessageToEachGroup=<{}>",
                            publish.msg().getClass().getCanonicalName(),
                            publish.topic(),
                            publish.sendOneMessageToEachGroup());
            pubSubMediator.tell(publish, responseCorrelationActor.get());
        }, devOpsErrorResponse -> {
            logger.withCorrelationId(command).warning("Dropping publishing command <{}>. Reason: <{}>", command,
                    devOpsErrorResponse.getDittoRuntimeException());
            getSender().tell(devOpsErrorResponse, getSelf());
        });
    }

    private void executeAsIndirectPiggybackCommandToPubSubMediator(final DevOpsCommand<?> command,
            final Supplier<ActorRef> responseCorrelationActor) {

        final String topic;
        final Optional<String> commandServiceNameOpt = command.getServiceName();
        if (commandServiceNameOpt.isPresent()) {
            final String commandServiceName = commandServiceNameOpt.get();
            final String commandInstance = command.getInstance().orElse(null);
            if (commandInstance != null) {
                topic = command.getType() + ":" + commandServiceName + ":" + commandInstance;
            } else {
                topic = command.getType() + ":" + commandServiceName;
            }
        } else {
            topic = command.getType();
        }
        final DistributedPubSubMediator.Publish msg;
        if (isGroupTopic(command.getDittoHeaders())) {
            msg = DistPubSubAccess.publishViaGroup(topic, command);
        } else {
            msg = DistPubSubAccess.publish(topic, command);
        }
        logger.withCorrelationId(command)
                .info("Publishing DevOpsCommand <{}> into cluster on topic <{}> with " +
                                "sendOneMessageToEachGroup=<{}>", command.getType(), msg.topic(),
                        msg.sendOneMessageToEachGroup());
        pubSubMediator.tell(msg, responseCorrelationActor.get());
    }

    private void tryInterpretAsDirectPublication(final DevOpsCommand<?> command,
            final Consumer<DistributedPubSubMediator.Publish> onSuccess,
            final Consumer<DevOpsErrorResponse> onError) {

        if (command instanceof ExecutePiggybackCommand executePiggyback) {
            final DittoHeaders dittoHeaders = executePiggyback.getDittoHeaders();
            deserializePiggybackCommand(executePiggyback,
                    jsonifiable -> {
                        final Optional<String> topic =
                                Optional.ofNullable(dittoHeaders.get(TOPIC_HEADER))
                                        .or(() -> executePiggyback.getPiggybackCommand()
                                                .getValue(Command.JsonFields.TYPE));
                        if (topic.isPresent()) {
                            if (isGroupTopic(dittoHeaders)) {
                                onSuccess.accept(DistPubSubAccess.publishViaGroup(topic.get(), jsonifiable));
                            } else {
                                onSuccess.accept(DistPubSubAccess.publish(topic.get(), jsonifiable));
                            }
                        } else {
                            onError.accept(getErrorResponse(command));
                        }
                    },
                    dittoRuntimeException -> onError.accept(getErrorResponse(command, dittoRuntimeException.toJson())));

        } else {
            // this should not happen
            final JsonObject error =
                    DittoInternalErrorException.newBuilder().dittoHeaders(command.getDittoHeaders()).build().toJson();
            onError.accept(getErrorResponse(command, error));
        }
    }

    private static boolean isGroupTopic(final DittoHeaders dittoHeaders) {
        final String isGroupTopicValue = dittoHeaders.get(IS_GROUP_TOPIC_HEADER);
        return isGroupTopicValue != null && !"false".equalsIgnoreCase(isGroupTopicValue);
    }

    private void handleDevOpsCommandViaPubSub(final DevOpsCommandViaPubSub devOpsCommandViaPubSub) {
        final DevOpsCommand<?> wrappedCommand = devOpsCommandViaPubSub.wrappedCommand;
        if (wrappedCommand instanceof ChangeLogLevel changeLogLevel) {
            handleChangeLogLevel(changeLogLevel);
        } else if (wrappedCommand instanceof RetrieveLoggerConfig retrieveLoggerConfig) {
            handleRetrieveLoggerConfig(retrieveLoggerConfig);
        } else if (wrappedCommand instanceof ExecutePiggybackCommand executePiggybackCommand) {
            handleExecutePiggyBack(executePiggybackCommand);
        }
    }

    private void handleChangeLogLevel(final ChangeLogLevel command) {
        final boolean isApplied = loggingFacade.setLogLevel(command.getLoggerConfig());
        final ChangeLogLevelResponse changeLogLevelResponse =
                ChangeLogLevelResponse.of(serviceName, instance, isApplied, command.getDittoHeaders());
        getSender().tell(changeLogLevelResponse, getSelf());
    }

    private void handleRetrieveLoggerConfig(final RetrieveLoggerConfig command) {
        final List<LoggerConfig> loggerConfigs;

        if (command.isAllKnownLoggers()) {
            loggerConfigs = loggingFacade.getLoggerConfig(command.includeDisabledLoggers());
        } else {
            loggerConfigs = loggingFacade.getLoggerConfig(command.getSpecificLoggers());
        }

        final RetrieveLoggerConfigResponse retrieveLoggerConfigResponse =
                RetrieveLoggerConfigResponse.of(serviceName, instance, loggerConfigs, command.getDittoHeaders());
        getSender().tell(retrieveLoggerConfigResponse, getSelf());
    }

    private void handleExecutePiggyBack(final ExecutePiggybackCommand command) {
        final ActorRef sender = getSender();
        deserializePiggybackCommand(command,
                jsonifiable -> {
                    logger.withCorrelationId(command)
                            .info("Received PiggybackCommand: <{}> - telling to: <{}>", jsonifiable,
                                    command.getTargetActorSelection());
                    final Duration timeout = command.getDittoHeaders().getTimeout().orElse(DEFAULT_RECEIVE_TIMEOUT);
                    final var actorSelection = getContext().actorSelection(command.getTargetActorSelection());
                    Patterns.ask(actorSelection, jsonifiable, timeout).whenComplete((result, error) -> {
                        final DevOpsCommandResponse<?> devOpsCommandResponse;
                        if (result instanceof JsonValueSourceRef) {
                            // to be streamed to sender
                            sender.tell(result, getSelf());
                            return;
                        } else if (result instanceof CommandResponse<?> response) {
                            devOpsCommandResponse =
                                    ExecutePiggybackCommandResponse.of(serviceName, instance, response.getHttpStatus(),
                                            response.toJson(), response.getDittoHeaders());
                        } else if (result instanceof DittoRuntimeException exception) {
                            devOpsCommandResponse =
                                    ExecutePiggybackCommandResponse.of(serviceName, instance, exception.getHttpStatus(),
                                            exception.toJson(), exception.getDittoHeaders());
                        } else if (result instanceof Jsonifiable<?> response) {
                            devOpsCommandResponse =
                                    ExecutePiggybackCommandResponse.of(serviceName, instance, HttpStatus.CONFLICT,
                                            response.toJson(), DittoHeaders.empty());
                        } else if (result != null) {
                            devOpsCommandResponse =
                                    ExecutePiggybackCommandResponse.of(serviceName, instance, HttpStatus.CONFLICT,
                                            JsonValue.of(Objects.toString(result)), DittoHeaders.empty());
                        } else {
                            devOpsCommandResponse = getErrorResponse(command,
                                    AskException.fromMessage(error != null ? error.getMessage() : "Unknown error occurred.",
                                            DittoHeaders.empty()).toJson());
                        }
                        sender.tell(devOpsCommandResponse, getSelf());
                    });
                },
                dittoRuntimeException -> sender.tell(getErrorResponse(command, dittoRuntimeException.toJson()),
                        getSelf()));
    }

    private void deserializePiggybackCommand(final ExecutePiggybackCommand command,
            final Consumer<Object> onSuccess, final Consumer<DittoRuntimeException> onError) {

        final JsonObject piggybackCommandJson = command.getPiggybackCommand();
        @Nullable final String piggybackCommandType = piggybackCommandJson.getValue(Command.JsonFields.TYPE)
                .orElse(null);
        if (POISON_PILL_NAME.equals(piggybackCommandType)) {
            onSuccess.accept(PoisonPill.getInstance());
        } else {
            final Consumer<JsonParsable<Jsonifiable<?>>> action = mappingStrategy -> {
                try {
                    onSuccess.accept(mappingStrategy.parse(piggybackCommandJson, command.getDittoHeaders()));
                } catch (final DittoRuntimeException e) {
                    logger.withCorrelationId(command)
                            .warning("Got DittoRuntimeException while parsing PiggybackCommand <{}>: {}!",
                                    piggybackCommandType, e);
                    onError.accept(e);
                }
            };
            final Runnable emptyAction = () -> {
                final String msgPattern =
                        "ExecutePiggybackCommand with PiggybackCommand <%s> cannot be executed by this"
                                + " service as there is no mapping strategy for it!";
                final String message = String.format(msgPattern, piggybackCommandType);
                logger.withCorrelationId(command).warning(message);
                onError.accept(JsonTypeNotParsableException.fromMessage(message, command.getDittoHeaders()));
            };
            serviceMappingStrategy.getMappingStrategy(piggybackCommandType).ifPresentOrElse(action, emptyAction);
        }
    }

    private DevOpsErrorResponse getErrorResponse(final DevOpsCommand<?> command, final JsonObject error) {
        final String responseServiceName = command.getServiceName().orElse(this.serviceName);
        final String responseInstance = command.getInstance().map(String::valueOf).orElse(this.instance);
        return DevOpsErrorResponse.of(responseServiceName, responseInstance, error, command.getDittoHeaders());
    }

    private DevOpsErrorResponse getErrorResponse(final DevOpsCommand<?> command) {
        final JsonObject error = JsonFactory.newObjectBuilder()
                .set(DittoRuntimeException.JsonFields.STATUS, HttpStatus.BAD_REQUEST.getCode())
                .set(DittoRuntimeException.JsonFields.MESSAGE,
                        "No topic found for publishing. Did you set the <topic> header?")
                .build();

        return getErrorResponse(command, error);
    }

    /**
     * Child actor handling the distributed pub/sub subscriptions of DevOpsCommands in the cluster.
     */
    private static final class PubSubSubscriberActor extends AbstractActor {

        private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

        @SuppressWarnings("unused")
        private PubSubSubscriberActor(final ActorRef pubSubMediator, final String serviceName, final String instance,
                final String... pubSubTopicsToSubscribeTo) {

            Arrays.stream(pubSubTopicsToSubscribeTo).forEach(topic ->
                    subscribeToDevOpsTopic(pubSubMediator, topic, serviceName, instance));
        }

        /**
         * @return the Akka configuration Props object.
         */
        static Props props(final ActorRef pubSubMediator,
                final String serviceName,
                final String instance,
                final String... pubSubTopicsToSubscribeTo) {

            return Props.create(PubSubSubscriberActor.class, pubSubMediator, serviceName, instance,
                    pubSubTopicsToSubscribeTo);
        }

        private void subscribeToDevOpsTopic(final ActorRef pubSubMediator,
                final String topic,
                final String serviceName,
                final String instance) {

            pubSubMediator.tell(DistPubSubAccess.subscribe(topic, getSelf()), getSelf());
            pubSubMediator.tell(DistPubSubAccess.subscribe(String.join(":", topic, serviceName), getSelf()), getSelf());
            pubSubMediator.tell(
                    DistPubSubAccess.subscribeViaGroup(String.join(":", topic, serviceName), serviceName, getSelf()),
                    getSelf());
            pubSubMediator.tell(DistPubSubAccess.subscribe(String.join(":", topic, serviceName, instance), getSelf()),
                    getSelf());
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(DevOpsCommand.class, command -> getContext().getParent().forward(
                            new DevOpsCommandViaPubSub(command), getContext()
                    ))
                    .match(DistributedPubSubMediator.SubscribeAck.class, this::handleSubscribeAck)
                    .matchAny(m -> {
                        log.warning(UNKNOWN_MESSAGE_TEMPLATE, m);
                        unhandled(m);
                    }).build();
        }

        private void handleSubscribeAck(final DistributedPubSubMediator.SubscribeAck subscribeAck) {
            log.info("Successfully subscribed to distributed pub/sub on topic <{}>.", subscribeAck.subscribe().topic());
        }
    }

    private static final class DevOpsCommandViaPubSub {

        private final DevOpsCommand<?> wrappedCommand;

        private DevOpsCommandViaPubSub(final DevOpsCommand<?> wrappedCommand) {
            this.wrappedCommand = wrappedCommand;
        }

    }

    /**
     * Child actor correlating the {@link org.eclipse.ditto.base.api.devops.signals.commands.DevOpsCommandResponse}s.
     * Waits for and collects responses until time runs out, then forwards all collected responses to the sender of the
     * original DevOps command.
     */
    private static final class DevOpsCommandResponseCorrelationActor extends AbstractActor {

        private static final boolean DEFAULT_AGGREGATE = true;

        private final ActorRef devOpsCommandSender;
        private final DevOpsCommand<?> devOpsCommand;
        private final List<CommandResponse<?>> commandResponses = new ArrayList<>();
        private final boolean aggregateResults;
        private final int expectedResponses;
        private final DittoDiagnosticLoggingAdapter logger;

        @SuppressWarnings("unused")
        private DevOpsCommandResponseCorrelationActor(final ActorRef devOpsCommandSender,
                final DevOpsCommand<?> devOpsCommand,
                final int expectedResponses) {

            this.devOpsCommandSender = devOpsCommandSender;
            this.devOpsCommand = devOpsCommand;

            final var dittoHeaders = devOpsCommand.getDittoHeaders();
            aggregateResults = isAggregateResults(dittoHeaders);
            this.expectedResponses = expectedResponses;
            logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
            logger.setCorrelationId(dittoHeaders);
        }

        private static boolean isAggregateResults(final DittoHeaders dittoHeaders) {
            var result = DEFAULT_AGGREGATE;
            final var aggregateHeaderValue = dittoHeaders.get(AGGREGATE_HEADER);
            if (null != aggregateHeaderValue) {
                result = Boolean.parseBoolean(aggregateHeaderValue);
            }
            return result;
        }

        /**
         * @return the Akka configuration Props object.
         */
        static Props props(final ActorRef devOpsCommandSender, final DevOpsCommand<?> devOpsCommand,
                final int expectedResponses) {
            return Props.create(DevOpsCommandResponseCorrelationActor.class, devOpsCommandSender, devOpsCommand,
                    expectedResponses);
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(CommandResponse.class, this::handleCommandResponse)
                    .match(JsonValueSourceRef.class, this::handleJsonValueSourceRef)
                    .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                    .match(ReceiveTimeout.class, receiveTimeout -> {
                        logger.withCorrelationId(getSelf().path().name())
                                .info("Got ReceiveTimeout, answering with all aggregated DevOpsCommandResponses and " +
                                        "stopping ourselves ...");
                        sendCommandResponsesAndStop();
                    })
                    .matchAny(m -> {
                        logger.warning(UNKNOWN_MESSAGE_TEMPLATE, m);
                        unhandled(m);
                    }).build();
        }

        private void handleCommandResponse(final CommandResponse<?> commandResponse) {
            if (commandResponse instanceof DevOpsCommandResponse) {
                logger.debug("Received DevOpsCommandResponse from service/instance <{}/{}>: {}",
                        ((DevOpsCommandResponse<?>) commandResponse).getServiceName().orElse("?"),
                        ((DevOpsCommandResponse<?>) commandResponse).getInstance().orElse("?"),
                        commandResponse.getType());
            } else {
                logger.debug("Received DevOpsCommandResponse from service/instance <?/?>: {}",
                        commandResponse.getType());
            }
            addCommandResponse(commandResponse);
        }

        private void addCommandResponse(final CommandResponse<?> commandResponse) {
            commandResponses.add(commandResponse);
            if (!aggregateResults || (expectedResponses >= 0 && commandResponses.size() >= expectedResponses)) {
                logger.info("All expected responses arrived. Sent response immediately.");
                sendCommandResponsesAndStop();
            }
        }

        private void sendCommandResponsesAndStop() {
            final AggregatedDevOpsCommandResponse response = AggregatedDevOpsCommandResponse.of(commandResponses,
                    DevOpsCommandResponse.TYPE_PREFIX + devOpsCommand.getName(),
                    commandResponses.isEmpty() ? HttpStatus.REQUEST_TIMEOUT : HttpStatus.OK,
                    devOpsCommand.getDittoHeaders(), aggregateResults);
            devOpsCommandSender.tell(response, getSelf());

            stopSelf();
        }

        private void stopSelf() {
            final var context = getContext();
            context.cancelReceiveTimeout();
            context.stop(getSelf());
        }

        private void handleJsonValueSourceRef(final JsonValueSourceRef jsonValueSourceRef) {
            logger.debug("Received {} from: <{}>", jsonValueSourceRef.getClass().getSimpleName(), getSender());
            devOpsCommandSender.tell(jsonValueSourceRef, getSelf());
            stopSelf();
        }

        private void handleDittoRuntimeException(final DittoRuntimeException dittoRuntimeException) {
            logger.warning("Received DittoRuntimeException from <{}>: <{}>!", getSender(), dittoRuntimeException);

            addCommandResponse(DevOpsErrorResponse.of(null, null, dittoRuntimeException.toJson(),
                    dittoRuntimeException.getDittoHeaders()));
        }

        @Override
        public void preStart() throws Exception {
            super.preStart();
            final var context = getContext();
            context.setReceiveTimeout(getReceiveTimeout());
        }

        private Duration getReceiveTimeout() {
            final var dittoHeaders = devOpsCommand.getDittoHeaders();
            final var timeout = dittoHeaders.getTimeout().orElse(DEFAULT_RECEIVE_TIMEOUT);
            return timeout.compareTo(DEFAULT_RECEIVE_TIMEOUT) > 0 ? timeout : DEFAULT_RECEIVE_TIMEOUT;
        }

    }

}
