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
package org.eclipse.ditto.thingsearch.service.starter.actors;

import static org.mockito.ArgumentMatchers.any;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.thingsearch.api.commands.sudo.SudoCountThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.CountThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.CountThingsResponse;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThingsResponse;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.common.model.ResultListImpl;
import org.eclipse.ditto.thingsearch.service.persistence.query.QueryParser;
import org.eclipse.ditto.thingsearch.service.persistence.read.ThingsSearchPersistence;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.stream.CompletionStrategy;
import akka.stream.OverflowStrategy;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests the graceful shutdown behavior of {@code SearchActor}.
 */
public final class SearchActorTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final Config CONFIG = ConfigFactory.load("actors-test.conf");

    @Rule
    public final ActorSystemResource actorSystemResource = ActorSystemResource.newInstance(CONFIG);

    private final ThingsSearchPersistence persistence = Mockito.mock(ThingsSearchPersistence.class);
    private QueryParser queryParser;

    @Before
    public void init() {
        final var searchConfig = DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(CONFIG));
        queryParser = SearchRootActor.getQueryParser(searchConfig, actorSystemResource.getActorSystem());
    }

    @Test
    public void unbindAndStopWithoutQuery() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            final var props = SearchActor.props(queryParser, persistence, getRef());
            final var underTest = childActorOf(props, SearchActor.ACTOR_NAME);

            final var expectedSubscribe =
                    DistPubSubAccess.subscribeViaGroup(ThingSearchCommand.TYPE_PREFIX, SearchActor.ACTOR_NAME,
                            underTest);
            expectMsg(expectedSubscribe);
            reply(new DistributedPubSubMediator.SubscribeAck(expectedSubscribe));

            underTest.tell(SearchActor.Control.SERVICE_UNBIND, getRef());
            final var expectedUnsubscribe = DistPubSubAccess.unsubscribeViaGroup(
                    ThingSearchCommand.TYPE_PREFIX, SearchActor.ACTOR_NAME, underTest);
            expectMsg(expectedUnsubscribe);
            reply(new DistributedPubSubMediator.UnsubscribeAck(expectedUnsubscribe));
            expectMsg(Done.getInstance());

            underTest.tell(SearchActor.Control.SERVICE_REQUESTS_DONE, getRef());
            expectMsg(Done.getInstance());

            // terminate actor in order not to prevent actor system shutdown
            underTest.tell(PoisonPill.getInstance(), getRef());
        }};
    }

    @Test
    public void waitForQueries() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            final var props = SearchActor.props(queryParser, persistence, getRef());
            final var underTest = childActorOf(props, SearchActor.ACTOR_NAME);

            final var expectedSubscribe =
                    DistPubSubAccess.subscribeViaGroup(ThingSearchCommand.TYPE_PREFIX, SearchActor.ACTOR_NAME,
                            underTest);
            expectMsg(expectedSubscribe);
            reply(new DistributedPubSubMediator.SubscribeAck(expectedSubscribe));

            final var serviceRequestsDone = SearchActor.Control.SERVICE_REQUESTS_DONE;
            final var countActor = use(p -> p.count(any(), any()));
            final var sudoCountActor = use(p -> p.sudoCount(any()));
            final var queryActor = use(p -> p.findAll(any(), any(), any()));
            final var shutdownProbe = TestProbe.apply(actorSystemResource.getActorSystem());

            final var headers = DittoHeaders.newBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            AuthorizationSubject.newInstance("ditto:ditto")))
                    .build();
            final var count = CountThings.of(headers);
            final var sudoCount = SudoCountThings.of(headers);
            final var query = QueryThings.of(headers);

            underTest.tell(count, getRef());
            underTest.tell(serviceRequestsDone, shutdownProbe.ref());
            shutdownProbe.expectNoMessage();
            countActor.tell(0L, ActorRef.noSender());
            countActor.tell("complete", ActorRef.noSender());
            expectMsg(CountThingsResponse.of(0, headers));
            shutdownProbe.expectMsg(Done.getInstance());

            underTest.tell(sudoCount, getRef());
            underTest.tell(serviceRequestsDone, shutdownProbe.ref());
            shutdownProbe.expectNoMessage();
            sudoCountActor.tell(0L, ActorRef.noSender());
            sudoCountActor.tell("complete", ActorRef.noSender());
            expectMsg(CountThingsResponse.of(0, headers));
            shutdownProbe.expectMsg(Done.getInstance());

            underTest.tell(query, getRef());
            underTest.tell(serviceRequestsDone, shutdownProbe.ref());
            shutdownProbe.expectNoMessage();
            queryActor.tell(new ResultListImpl<>(List.of(), -1), ActorRef.noSender());
            queryActor.tell("complete", ActorRef.noSender());
            expectMsgClass(QueryThingsResponse.class);
            shutdownProbe.expectMsg(Done.getInstance());

            // terminate actor in order not to prevent actor system shutdown
            underTest.tell(PoisonPill.getInstance(), getRef());
        }};
    }

    private ActorRef use(final Consumer<ThingsSearchPersistence> stubberConsumer) {
        final akka.japi.function.Function<Object, Optional<CompletionStrategy>> completionStrategy =
                msg -> Optional.of(msg).filter("complete"::equals).map(m -> CompletionStrategy.draining());
        final akka.japi.function.Function<Object, Optional<Throwable>> failureMatcher = msg -> Optional.empty();
        final var mat =
                Source.actorRef(completionStrategy, failureMatcher, 16, OverflowStrategy.dropHead())
                        .preMaterialize(SystemMaterializer.get(actorSystemResource.getActorSystem()).materializer());
        stubberConsumer.accept(Mockito.doAnswer(inv -> mat.second()).when(persistence));

        return mat.first();
    }

}
