/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.ditto.thingsearch.service.persistence.read.criteria.visitors;

import java.util.Map;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Test;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;

public class CreateBsonAggregationPredicateVisitorTest {


    @Test
    public void aggregationCondFromRqlFilterToBson() throws JSONException {
        final String rqlFilter = "and(eq(attributes/Info/gateway,true),gt(_created,\"2024-02-15T09:00\"),lt(features/ConnectionStatus/properties/status/readyUntil/,time:now),not(exists(features/GatewayServices)))";
        final QueryFilterCriteriaFactory criteriaFactory =
                QueryFilterCriteriaFactory.of(ThingsFieldExpressionFactory.of(Map.of(
                                "_created", "/_created"
                        )),
                        RqlPredicateParser.getInstance());
        final Criteria criteria = criteriaFactory.filterCriteria(rqlFilter, DittoHeaders.empty());
        final Bson bson = CreateBsonAggregationVisitor.sudoApply(criteria);
        final CustomComparator comparator = new CustomComparator(JSONCompareMode.LENIENT,
                new Customization("$lt", (o1, o2) -> {
                    if (o1 instanceof JSONArray array1 && o2 instanceof JSONArray array2) {
                        try {
                            return (array1.length() == array2.length()) &&
                                    array1.getString(0).equals(array2.getString(0));
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return false;
                }));
        final String expectedCond =
                "{\"$and\": [{\"$eq\": [\"$t.attributes.Info.gateway\", true]}, {\"$gt\": [\"$t._created\", \"2024-02-15T09:00\"]}, {\"$lt\": [\"$t.features.ConnectionStatus.properties.status.readyUntil\", \"2024-01-01T00:00:00.000000Z\"]}, {\"$nor\": [{\"t.features.GatewayServices\": {\"$exists\": true}}]}]}";
        final BsonDocument expected = BsonDocument.parse(expectedCond);
        JSONAssert.assertEquals(expected.toJson(), bson.toBsonDocument().toJson(), comparator);
    }
}