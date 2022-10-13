/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.service.CompletableFutureUtils;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.mqtt.SessionExpiryInterval;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.ChildActorNanny;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.KeepAliveInterval;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttClientFactory;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.HiveMqttClientProperties;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.MqttSubscribeException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.NoMqttConnectionException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.SubscriptionStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.SubscriptionsFailedException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.consuming.MqttConsumerActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.connect.GenericMqttConnect;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing.MqttPublisherActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.MqttSubscriber;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.SubscribeResult;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.health.RetrieveHealth;
import org.eclipse.ditto.internal.utils.health.RetrieveHealthResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;

/**
 * Tests whether an MQTT {@link org.eclipse.ditto.connectivity.model.Connection} can successfully be established
 * by connecting to the MQTT broker, starting publisher and consumer actors and checking their health.
 */
final class ConnectionTesterActor extends AbstractActor {

    /**
     * Maximum duration of ask interactions.
     */
    static final Duration ASK_TIMEOUT = Duration.ofSeconds(10L);

    private final ConnectivityConfig connectivityConfig;
    private final Supplier<SshTunnelState> sshTunnelStateSupplier;
    private final ConnectionLogger connectionLogger;
    private final UUID actorUuid;
    private final ConnectivityStatusResolver connectivityStatusResolver;
    private final ThreadSafeDittoLoggingAdapter logger;
    private final ChildActorNanny childActorNanny;
    private final GenericMqttClientFactory genericMqttClientFactory;

    @SuppressWarnings("java:S1144")
    private ConnectionTesterActor(final ConnectivityConfig connectivityConfig,
            final Supplier<SshTunnelState> sshTunnelStateSupplier,
            final ConnectionLogger connectionLogger,
            final UUID actorUuid,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final GenericMqttClientFactory genericMqttClientFactory) {

        this.connectivityConfig = connectivityConfig;
        this.sshTunnelStateSupplier = sshTunnelStateSupplier;
        this.connectionLogger = connectionLogger;
        this.actorUuid = actorUuid;
        this.connectivityStatusResolver = connectivityStatusResolver;
        this.genericMqttClientFactory = genericMqttClientFactory;

        logger = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);
        childActorNanny = ChildActorNanny.newInstance(getContext(), logger);
    }

    /**
     * Returns the {@code Props} for creating a {@code ConnectionTesterActor} with the specified arguments.
     *
     * @param connectivityConfig the config of Connectivity service with potential overwrites.
     * @param sshTunnelStateSupplier supplies the {@code SshTunnelState} for connection testing.
     * @param connectionLogger logs the result of connection testing for end users.
     * @param parentActorUuid UUID of the parent actor.
     * @param connectivityStatusResolver resolves occurred exceptions to a connectivity status.
     * @param genericMqttClientFactory factory for creating the GenericMqttClient for connection testing.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Props props(final ConnectivityConfig connectivityConfig,
            final Supplier<SshTunnelState> sshTunnelStateSupplier,
            final ConnectionLogger connectionLogger,
            final UUID parentActorUuid,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final GenericMqttClientFactory genericMqttClientFactory) {

        return Props.create(
                ConnectionTesterActor.class,
                checkNotNull(connectivityConfig, "connectivityConfig"),
                checkNotNull(sshTunnelStateSupplier, "sshTunnelStateSupplier"),
                checkNotNull(connectionLogger, "connectionLogger"),
                checkNotNull(parentActorUuid, "parentActorUuid"),
                checkNotNull(connectivityStatusResolver, "connectivityStatusResolver"),
                checkNotNull(genericMqttClientFactory, "genericMqttClientFactory")
        );
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(TestConnection.class, this::testConnection)
                .match(StartChildActorConflictFree.class, this::startChildActorConflictFree)
                .build();
    }

    private void testConnection(final TestConnection testConnection) {
        final var testConnectionContext = new TestConnectionContext(getSender(), testConnection);
        getClientPropertiesContext(testConnectionContext)
                .thenApply(this::getClientContext)
                .thenCompose(clientContext -> connectClient(clientContext)
                        .thenApply(logMqttConnectionEstablished())
                        .thenCompose(startPublisherActor())
                        .thenCombine(subscribe(clientContext), handleTotalSubscribeResult())
                        .thenCompose(getChildActorStatuses())
                        .whenComplete((childActorStatusesContext, error) -> {
                            if (null == error) {
                                replyWithTotalStatus(childActorStatusesContext);
                            }
                            disconnectMqttClient(clientContext);
                        }))
                .whenComplete((childActorStatusesContext, throwable) -> {
                    if (null != throwable) {
                        tellStatusToOriginalSender(logFailureAndGetStatus(throwable, testConnectionContext),
                                testConnectionContext.originalSender());
                    }
                });
    }

    private CompletionStage<ClientPropertiesContext> getClientPropertiesContext(
            final TestConnectionContext testConnectionContext
    ) {
        return tryToGetHiveMqttClientProperties(testConnectionContext)
                .thenApply(clientProperties -> new ClientPropertiesContext(testConnectionContext, clientProperties));
    }

    private CompletionStage<HiveMqttClientProperties> tryToGetHiveMqttClientProperties(final WithConnection context) {
        final var connection = context.connection();
        final var connectionConfig = connectivityConfig.getConnectionConfig();
        try {
            return CompletableFuture.completedFuture(HiveMqttClientProperties.builder()
                    .withMqttConnection(connection)
                    .withConnectivityConfig(connectivityConfig)
                    .withMqttSpecificConfig(MqttSpecificConfig.fromConnection(connection,
                            connectionConfig.getMqttConfig()))
                    .withSshTunnelStateSupplier(sshTunnelStateSupplier)
                    .withConnectionLogger(connectionLogger)
                    .withActorUuid(actorUuid)
                    .disableLastWillMessage() // no Last Will Message for connection testing
                    .build());
        } catch (final NoMqttConnectionException e) {
            logger.withCorrelationId(context.correlationId())
                    .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, context.connectionId())
                    .info("Failed to create {}: {}", HiveMqttClientProperties.class.getSimpleName(), e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    private ClientContext getClientContext(final ClientPropertiesContext ctx) {
        try {
            return new ClientContext(ctx, genericMqttClientFactory.getGenericMqttClient(ctx.clientProperties()));
        } catch (final Exception e) {
            logger.withCorrelationId(ctx.correlationId())
                    .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, ctx.connectionId())
                    .info("Failed to create {}: {}", GenericMqttClient.class.getSimpleName(), e.getMessage());
            throw e;
        }
    }

    private static CompletionStage<ClientContext> connectClient(final ClientContext clientContext) {
        final var hiveMqttClientProperties = clientContext.clientProperties();
        final var mqttConfig = hiveMqttClientProperties.getMqttConfig();
        final var client = clientContext.genericMqttClient();

        return client.connect(GenericMqttConnect.newInstance(true,
                        KeepAliveInterval.zero(),
                        SessionExpiryInterval.defaultSessionExpiryInterval(),
                        mqttConfig.getClientReceiveMaximum()))
                .thenApply(unusedVoid -> clientContext);
    }

    private Function<ClientContext, ClientContext> logMqttConnectionEstablished() {
        return clientContext -> {
            final var hiveMqttClientProperties = clientContext.clientProperties();
            final var mqttConnection = hiveMqttClientProperties.getMqttConnection();
            logger.info("Established MQTT connection to <{}>.", mqttConnection.getUri());
            return clientContext;
        };
    }

    private Function<ClientContext, CompletionStage<PublisherActorRefContext>> startPublisherActor() {
        return clientContext -> Patterns.ask(getSelf(),
                        new StartChildActorConflictFree(MqttPublisherActor.class.getSimpleName(),
                                MqttPublisherActor.propsDryRun(clientContext.connection(),
                                        connectivityStatusResolver,
                                        connectivityConfig,
                                        clientContext.genericMqttClient())),
                        ASK_TIMEOUT)
                .thenApply(ActorRef.class::cast)
                .thenApply(publisherActorRef -> new PublisherActorRefContext(clientContext, publisherActorRef));
    }

    private CompletionStage<TotalSubscribeResult> subscribe(final ClientContext clientContext) {
        final var mqttConnection = clientContext.connection();
        final var mqttSubscriber = MqttSubscriber.newInstance(clientContext.genericMqttClient());
        return mqttSubscriber.subscribeForConnectionSources(mqttConnection.getSources())
                .toMat(Sink.seq(), Keep.right())
                .run(getContext().getSystem())
                .thenApply(TotalSubscribeResult::of);
    }

    private BiFunction<PublisherActorRefContext, TotalSubscribeResult, CompletionStage<ConsumerActorRefsContext>> handleTotalSubscribeResult() {
        return (publisherActorRefContext, totalSubscribeResult) -> {
            if (totalSubscribeResult.hasFailures()) {
                throw newMqttSubscribeExceptionForFailedSourceSubscribeResults(totalSubscribeResult);
            } else {
                return CompletableFutureUtils.collectAsList(totalSubscribeResult.successfulSubscribeResults()
                                .map(subscribeResult -> startConsumerActor(subscribeResult,
                                        publisherActorRefContext.clientProperties()))
                                .map(CompletionStage::toCompletableFuture)
                                .toList())
                        .thenApply(consumerActorRefs -> new ConsumerActorRefsContext(publisherActorRefContext,
                                consumerActorRefs));
            }
        };
    }

    private static MqttSubscribeException newMqttSubscribeExceptionForFailedSourceSubscribeResults(
            final TotalSubscribeResult totalSubscribeResult
    ) {
        throw new MqttSubscribeException(
                totalSubscribeResult.failedSubscribeResults()
                        .flatMap(sourceSubscribeResult -> {
                            final Stream<String> result;
                            final var mqttSubscribeException = sourceSubscribeResult.getErrorOrThrow();
                            if (mqttSubscribeException instanceof SubscriptionsFailedException subscriptionsFailedException) {
                                result = subscriptionsFailedException.failedSubscriptionStatuses()
                                        .map(SubscriptionStatus::toString);
                            } else {
                                final var connectionSource = sourceSubscribeResult.getConnectionSource();
                                result = Stream.of(MessageFormat.format("{0}: {1}",
                                        mqttSubscribeException.getMessage(),
                                        connectionSource.getAddresses()));
                            }
                            return result;
                        })
                        .collect(Collectors.joining(", ", "[", "]")),
                null
        );
    }

    private CompletionStage<ActorRef> startConsumerActor(
            final SubscribeResult subscribeSuccess,
            final HiveMqttClientProperties hiveMqttClientProperties
    ) {
        return Patterns.ask(getSelf(),
                        new StartChildActorConflictFree(
                                MqttConsumerActor.class.getSimpleName(),
                                MqttConsumerActor.propsDryRun(hiveMqttClientProperties.getMqttConnection(),
                                        Sink.onComplete(param -> {}),
                                        subscribeSuccess.getConnectionSource(),
                                        connectivityStatusResolver,
                                        hiveMqttClientProperties.getConnectivityConfig(),
                                        subscribeSuccess.getMqttPublishSourceOrThrow())
                        ),
                        ASK_TIMEOUT)
                .thenApply(ActorRef.class::cast);
    }

    private static Function<CompletionStage<ConsumerActorRefsContext>, CompletionStage<ChildActorStatusesContext>> getChildActorStatuses() {
        return consumerActorRefsContextFuture -> consumerActorRefsContextFuture.thenCompose(
                consumerActorRefsContext -> {
                    final var consumerActorRefs = consumerActorRefsContext.consumerActorRefs();
                    final var childActorRefs = Stream.concat(
                            Stream.of(consumerActorRefsContext.publisherActorRef()),
                            consumerActorRefs.stream()
                    );
                    return CompletableFutureUtils.collectAsList(childActorRefs
                                    .map(ConnectionTesterActor::getActorStatus)
                                    .map(CompletionStage::toCompletableFuture)
                                    .toList())
                            .thenApply(childActorStatuses -> new ChildActorStatusesContext(consumerActorRefsContext,
                                    childActorStatuses));
                }
        );
    }

    private static CompletionStage<Status.Status> getActorStatus(final ActorRef actorRef) {
        return Patterns.ask(actorRef, RetrieveHealth.newInstance(), ASK_TIMEOUT)
                .thenApply(response -> {
                    final Status.Status result;
                    if (response instanceof RetrieveHealthResponse retrieveHealthResponse) {
                        final var statusInfo = retrieveHealthResponse.getStatusInfo();
                        if (statusInfo.isHealthy()) {
                            result = new Status.Success(statusInfo.getStatus());
                        } else {
                            result = new Status.Failure(new IllegalStateException(
                                    MessageFormat.format("Actor <{0}> has status <{1}>.",
                                            actorRef.path(),
                                            statusInfo.getStatus())
                            ));
                        }
                    } else {
                        result = new Status.Failure(new IllegalStateException(
                                MessageFormat.format("Actor <{0}> did not respond with a {1} but with a <{2}>.",
                                        actorRef.path(),
                                        RetrieveHealthResponse.class.getSimpleName(),
                                        response.getClass().getSimpleName())
                        ));
                    }
                    return result;
                })
                .exceptionally(throwable -> {
                    final Status.Status result;
                    final var error = unwrapCauseIfCompletionException(throwable);
                    if (error instanceof AskTimeoutException) {
                        result = new Status.Failure(new IllegalStateException(
                                MessageFormat.format("Actor <{0}> did not report its status within <{1}>.",
                                        actorRef.path(),
                                        ASK_TIMEOUT),
                                error
                        ));
                    } else {
                        result = new Status.Failure(error);
                    }
                    return result;
                });
    }

    private static Throwable unwrapCauseIfCompletionException(final Throwable throwable) {
        final Throwable result;
        if (throwable instanceof CompletionException e) {
            result = e.getCause();
        } else {
            result = throwable;
        }
        return result;
    }

    private void replyWithTotalStatus(final ChildActorStatusesContext childActorStatusesContext) {
        final Status.Status totalStatus;
        final var failures = childActorStatusesContext.childActorFailures();
        if (failures.isEmpty()) {
            totalStatus = handleConnectionTestSuccess(childActorStatusesContext);
        } else {
            if (1 == failures.size()) {
                final var failure = failures.get(0);
                totalStatus = logFailureAndGetStatus(failure.cause(), childActorStatusesContext);
            } else {
                totalStatus = logFailureAndGetStatus(getIllegalStateExceptionForMultipleFailures(failures),
                        childActorStatusesContext);
            }
        }
        tellStatusToOriginalSender(totalStatus, childActorStatusesContext.originalSender());

        // Error handling is done at a higher stage.
    }

    private Status.Status handleConnectionTestSuccess(final ChildActorStatusesContext childActorStatusesContext) {
        final var mqttConnection = childActorStatusesContext.connection();

        logger.withCorrelationId(childActorStatusesContext.correlationId())
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, mqttConnection.getId())
                .info("Test for connection <{}> at <{}> was successful.",
                        mqttConnection.getId(),
                        mqttConnection.getUri());

        final var successMessage = "Connection test was successful.";
        connectionLogger.success(successMessage);

        return new Status.Success(successMessage);
    }

    private Status.Status logFailureAndGetStatus(final Throwable throwable, final WithConnection context) {
        final var mqttConnection = context.connection();
        final var error = unwrapCauseIfCompletionException(throwable);

        logger.withCorrelationId(context.correlationId())
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, mqttConnection.getId())
                .info("Test for connection <{}> at <{}> failed: {}",
                        mqttConnection.getId(),
                        mqttConnection.getUri(),
                        error.getMessage());

        connectionLogger.failure("Connection test failed: {0}", error.getMessage());

        return new Status.Failure(error);
    }

    private static IllegalStateException getIllegalStateExceptionForMultipleFailures(
            final Collection<Status.Failure> failures
    ) {
        return new IllegalStateException(failures.stream()
                .map(Status.Failure::cause)
                .map(Throwable::getMessage)
                .collect(Collectors.joining(", ", "[", "]")));
    }

    private void tellStatusToOriginalSender(final Status.Status status, final ActorRef originalSender) {
        originalSender.tell(status, getSelf());
    }

    private static void disconnectMqttClient(final WithClient withClient) {
        final var genericMqttClient = withClient.genericMqttClient();
        genericMqttClient.disconnect();
    }

    private void startChildActorConflictFree(final StartChildActorConflictFree command) {
        final var sender = getSender();
        sender.tell(childActorNanny.startChildActorConflictFree(command.baseActorName(), command.props()),
                getSelf());
    }

    private record StartChildActorConflictFree(CharSequence baseActorName, Props props) {}

    private interface WithOriginalSender {

        ActorRef originalSender();

        @Nullable
        CharSequence correlationId();

    }

    private interface WithConnection extends WithOriginalSender {

        Connection connection();

        default ConnectionId connectionId() {
            final var connection = connection();
            return connection.getId();
        }

    }

    private interface WithClientProperties extends WithConnection {

        HiveMqttClientProperties clientProperties();

        @Override
        default Connection connection() {
            final var clientProperties = clientProperties();
            return clientProperties.getMqttConnection();
        }

    }

    private interface WithClient extends WithClientProperties {

        GenericMqttClient genericMqttClient();

    }

    private interface WithPublisherActorRef extends WithClient {

        ActorRef publisherActorRef();

    }

    private interface WithConsumerActorRefs extends WithPublisherActorRef {

        List<ActorRef> consumerActorRefs();

    }

    private record TestConnectionContext(
            ActorRef originalSender,
            TestConnection testConnection
    ) implements WithConnection {

        @Override
        public Connection connection() {
            return testConnection.getConnection();
        }

        @Nullable
        @Override
        public CharSequence correlationId() {
            final var testConnectionDittoHeaders = testConnection.getDittoHeaders();
            return testConnectionDittoHeaders.getCorrelationId().orElse(null);
        }

    }

    private record ClientPropertiesContext(
            TestConnectionContext testConnectionContext,
            HiveMqttClientProperties clientProperties
    ) implements WithClientProperties {

        @Override
        public ActorRef originalSender() {
            return testConnectionContext.originalSender();
        }

        @Nullable
        @Override
        public CharSequence correlationId() {
            return testConnectionContext.correlationId();
        }

    }

    private record ClientContext(
            ClientPropertiesContext clientPropertiesContext,
            GenericMqttClient genericMqttClient
    ) implements WithClient {

        @Override
        public ActorRef originalSender() {
            return clientPropertiesContext.originalSender();
        }

        @Nullable
        @Override
        public CharSequence correlationId() {
            return clientPropertiesContext.correlationId();
        }

        @Override
        public HiveMqttClientProperties clientProperties() {
            return clientPropertiesContext.clientProperties();
        }

    }

    private record PublisherActorRefContext(
            ClientContext clientContext,
            ActorRef publisherActorRef
    ) implements WithPublisherActorRef {

        @Override
        public ActorRef originalSender() {
            return clientContext.originalSender();
        }

        @Nullable
        @Override
        public CharSequence correlationId() {
            return clientContext.correlationId();
        }

        @Override
        public HiveMqttClientProperties clientProperties() {
            return clientContext.clientProperties();
        }

        @Override
        public GenericMqttClient genericMqttClient() {
            return clientContext.genericMqttClient();
        }

    }

    private record ConsumerActorRefsContext(
            PublisherActorRefContext publisherActorRefContext,
            List<ActorRef> consumerActorRefs
    ) implements WithConsumerActorRefs {

        @Override
        public ActorRef originalSender() {
            return publisherActorRefContext.originalSender();
        }

        @Nullable
        @Override
        public CharSequence correlationId() {
            return publisherActorRefContext.correlationId();
        }

        @Override
        public HiveMqttClientProperties clientProperties() {
            return publisherActorRefContext.clientProperties();
        }

        @Override
        public GenericMqttClient genericMqttClient() {
            return publisherActorRefContext.genericMqttClient();
        }

        @Override
        public ActorRef publisherActorRef() {
            return publisherActorRefContext.publisherActorRef();
        }

    }

    private record ChildActorStatusesContext(
            ConsumerActorRefsContext consumerActorRefsContext,
            List<Status.Status> childActorStatuses
    ) implements WithConsumerActorRefs {

        @Override
        public ActorRef originalSender() {
            return consumerActorRefsContext.originalSender();
        }

        @Nullable
        @Override
        public CharSequence correlationId() {
            return consumerActorRefsContext.correlationId();
        }

        @Override
        public HiveMqttClientProperties clientProperties() {
            return consumerActorRefsContext.clientProperties();
        }

        @Override
        public GenericMqttClient genericMqttClient() {
            return consumerActorRefsContext.genericMqttClient();
        }

        @Override
        public ActorRef publisherActorRef() {
            return consumerActorRefsContext.publisherActorRef();
        }

        @Override
        public List<ActorRef> consumerActorRefs() {
            return consumerActorRefsContext.consumerActorRefs();
        }

        List<Status.Failure> childActorFailures() {
            return childActorStatuses.stream()
                    .filter(status -> status instanceof Status.Failure)
                    .map(Status.Failure.class::cast)
                    .toList();
        }

    }

    /**
     * Bundles a finite amount of {@link SubscribeResult}s for determining failures.
     */
    static final class TotalSubscribeResult {

        private final List<SubscribeResult> successfulSubscribeResults;
        private final List<SubscribeResult> failedSubscribeResults;

        private TotalSubscribeResult(final Collection<SubscribeResult> successfulSubscribeResults,
                final Collection<SubscribeResult> failedSubscribeResults) {

            this.successfulSubscribeResults = List.copyOf(successfulSubscribeResults);
            this.failedSubscribeResults = List.copyOf(failedSubscribeResults);
        }

        /**
         * Returns an instance of {@code TotalSubscribeResult} for the specified list argument.
         *
         * @param sourceSubscribeResults a list of {@code SubscribeResult}s which are regarded as a whole to represent
         * a total subscribe result.
         * @return the instance.
         * @throws NullPointerException if {@code sourceSubscribeResults} is {@code null}.
         */
        static TotalSubscribeResult of(final List<SubscribeResult> sourceSubscribeResults) {
            checkNotNull(sourceSubscribeResults, "sourceSubscribeResults");
            final var subscribeResultsByIsSuccess =
                    sourceSubscribeResults.stream().collect(Collectors.partitioningBy(SubscribeResult::isSuccess));
            return new TotalSubscribeResult(subscribeResultsByIsSuccess.get(true),
                    subscribeResultsByIsSuccess.get(false));
        }

        boolean hasFailures() {
            return !failedSubscribeResults.isEmpty();
        }

        Stream<SubscribeResult> successfulSubscribeResults() {
            return successfulSubscribeResults.stream();
        }

        Stream<SubscribeResult> failedSubscribeResults() {
            return failedSubscribeResults.stream();
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final var that = (TotalSubscribeResult) o;
            return Objects.equals(successfulSubscribeResults, that.successfulSubscribeResults) &&
                    Objects.equals(failedSubscribeResults, that.failedSubscribeResults);
        }

        @Override
        public int hashCode() {
            return Objects.hash(successfulSubscribeResults, failedSubscribeResults);
        }

    }

}
