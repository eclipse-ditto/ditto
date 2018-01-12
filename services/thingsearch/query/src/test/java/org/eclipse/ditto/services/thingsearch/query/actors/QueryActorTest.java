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
package org.eclipse.ditto.services.thingsearch.query.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Predicate;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.visitors.PredicateVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FilterFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.query.Query;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryBuilder;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryBuilderFactory;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidFilterException;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;

/**
 * Unit test for {@link QueryActor}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class QueryActorTest {

    private static final String KNOWN_PROPERTY = "thingId";
    private static final String KNOWN_FILTER = "eq(" + KNOWN_PROPERTY + ",4711)";
    private static final String KNOWN_INVALID_FILTER = "bumlux(" + KNOWN_PROPERTY + ",4711)";
    private static final String KNOWN_OPTION = "limit(10,10)";
    private static final String KNOWN_NAMESPACES = "com.bosch.test";
    private static final DittoHeaders KNOWN_DITTO_HEADERS = DittoHeaders.newBuilder()
            .correlationId("someCorrelationId")
            .authorizationContext(AuthorizationContext.newInstance(AuthorizationSubject.newInstance("authSubject")))
            .schemaVersion(JsonSchemaVersion.V_1)
            .build();

    private ActorSystem actorSystem;

    @Mock
    private CriteriaFactory criteriaFactoryMock;
    @Mock
    private ThingsFieldExpressionFactory thingsFieldExpressionFactoryMock;
    @Mock
    private FilterFieldExpression thingIdExpressionMock;
    @Mock
    private QueryBuilderFactory queryBuilderFactoryMock;
    @Mock
    private QueryBuilder queryBuilderMock;
    @Mock
    private Query queryMock;
    @Mock
    private Criteria criteriaMock;

    @Before
    public void setUp() throws Exception {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);

        when(criteriaFactoryMock.eq(any())).thenReturn(new Predicate() {
            @Override
            public <T> T accept(final PredicateVisitor<T> visitor) {
                return null;
            }
        });
        when(thingsFieldExpressionFactoryMock.filterBy(KNOWN_PROPERTY)).thenReturn(thingIdExpressionMock);
        when(queryBuilderFactoryMock.newUnlimitedBuilder(any())).thenReturn(queryBuilderMock);
        when(queryBuilderFactoryMock.newBuilder(any())).thenReturn(queryBuilderMock);
        when(queryBuilderMock.skip(anyLong())).thenReturn(queryBuilderMock);
        when(queryBuilderMock.limit(anyLong())).thenReturn(queryBuilderMock);
        when(queryBuilderMock.build()).thenReturn(queryMock);
        when(queryMock.getCriteria()).thenReturn(criteriaMock);
    }

    @After
    public void tearDown() throws Exception {
        JavaTestKit.shutdownActorSystem(actorSystem);
        actorSystem = null;
    }

    @Test
    public void countThingsToQuery() {
        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createQueryActor();

                underTest.tell(CountThings.of(KNOWN_FILTER, Collections.singleton(KNOWN_NAMESPACES),
                        KNOWN_DITTO_HEADERS), getRef());
                final Query query = expectMsgClass(Query.class);

                assertThat(query.getCriteria()).isEqualTo(criteriaMock);
            }
        };
    }

    @Test
    public void queryThingsToQuery() {
        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createQueryActor();

                underTest.tell(QueryThings.of(KNOWN_FILTER, KNOWN_DITTO_HEADERS), getRef());
                final Query query = expectMsgClass(Query.class);

                assertThat(query.getCriteria()).isEqualTo(criteriaMock);
            }
        };
    }

    @Test
    public void queryThingsToQueryWithOptions() {
        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createQueryActor();

                underTest.tell(QueryThings.of(KNOWN_FILTER, Collections.singletonList(KNOWN_OPTION),
                        Collections.singleton(KNOWN_NAMESPACES),
                        KNOWN_DITTO_HEADERS), getRef());
                final Query query = expectMsgClass(Query.class);

                assertThat(query.getCriteria()).isEqualTo(criteriaMock);
            }
        };
    }

    @Test
    public void queryThingsToQueryWithInvalidFilter() {
        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createQueryActor();

                underTest.tell(QueryThings.of(KNOWN_INVALID_FILTER, KNOWN_DITTO_HEADERS), getRef());
                final InvalidFilterException invalidFilterException = expectMsgClass(InvalidFilterException.class);

                assertThat(invalidFilterException.getDittoHeaders()).isEqualTo(KNOWN_DITTO_HEADERS);
            }
        };
    }

    private ActorRef createQueryActor() {
        return actorSystem.actorOf(QueryActor.props(criteriaFactoryMock,
                thingsFieldExpressionFactoryMock, queryBuilderFactoryMock), QueryActor.ACTOR_NAME);
    }

}
