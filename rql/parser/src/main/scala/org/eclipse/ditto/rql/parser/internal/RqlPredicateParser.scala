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
package org.eclipse.ditto.rql.parser.internal

import akka.parboiled2._
import org.eclipse.ditto.rql.model.ParserException
import org.eclipse.ditto.rql.model.predicates.PredicateParser
import org.eclipse.ditto.rql.model.predicates.ast.SingleComparisonNode.Type
import org.eclipse.ditto.rql.model.predicates.ast._

import scala.collection.immutable
import scala.jdk.javaapi.CollectionConverters
import scala.util.{Failure, Success}

/**
  * RQL Parser. Parses predicates in the RQL "standard" according to https://github.com/persvr/rql with the following
  * EBNF:
  * <pre>
  * Query                      = SingleComparisonOp | MultiComparisonOp | MultiLogicalOp | SingleLogicalOp | ExistsOp
  * SingleComparisonOp         = SingleComparisonName, '(', ComparisonProperty, ',', ComparisonValue, ')'
  * SingleComparisonName       = "eq" | "ne" | "gt" | "ge" | "lt" | "le" | "like" | "ilike"
  * MultiComparisonOp          = MultiComparisonName, '(', ComparisonProperty, ',', ComparisonValue, { ',', ComparisonValue }, ')'
  * MultiComparisonName        = "in"
  * MultiLogicalOp             = MultiLogicalName, '(', Query, { ',', Query }, ')'
  * MultiLogicalName           = "and" | "or"
  * SingleLogicalOp            = SingleLogicalName, '(', Query, ')'
  * SingleLogicalName          = "not"
  * ExistsOp                   = "exists" '(', ComparisonProperty, ')'
  *
  * ComparisonProperty         = PropertyLiteral
  * ComparisonValue            = Literal
  * </pre>
  */
private class RqlPredicateParser(override val input: ParserInput) extends RqlParserBase(input) {

  /**
    * @return the root for parsing an RQL Predicate.
    */
  def PredicateRoot: Rule1[Node] = rule {
    WhiteSpace ~ Query ~ EOI
  }

  /**
    * Query                      = SingleComparisonOp | MultiComparisonOp | MultiLogicalOp | SingleLogicalOp | ExistsOp
    */
  private def Query: Rule1[Node] = rule {
    SingleComparisonOp | MultiComparisonOp | MultiLogicalOp | SingleLogicalOp | ExistsOp
  }

  /**
    * SingleComparisonOp         = SingleComparisonName, '(', ComparisonProperty, ',', ComparisonValue, ')'
    */
  private def SingleComparisonOp: Rule1[Node] = rule {
    SingleComparisonName ~ '(' ~ ComparisonProperty ~ ',' ~ ComparisonValue ~ ')' ~>
      ((compType: Type, compProp: String, compValue: java.lang.Object) => {
        compValue match {
          case None => new SingleComparisonNode(compType, compProp, null)
          case _ => new SingleComparisonNode(compType, compProp, compValue)
        }
      })
  }

  /**
    * SingleComparisonName       = "eq" | "ne" | "gt" | "ge" | "lt" | "le" | "like" | "ilike"
    */
  private def SingleComparisonName: Rule1[SingleComparisonNode.Type] = rule {
    eq | ne | gt | ge | lt | le | like | ilike
  }

  private def eq: Rule1[SingleComparisonNode.Type] = rule {
    "eq" ~ push(SingleComparisonNode.Type.EQ)
  }

  private def ne: Rule1[SingleComparisonNode.Type] = rule {
    "ne" ~ push(SingleComparisonNode.Type.NE)
  }

  private def gt: Rule1[SingleComparisonNode.Type] = rule {
    "gt" ~ push(SingleComparisonNode.Type.GT)
  }

  private def ge: Rule1[SingleComparisonNode.Type] = rule {
    "ge" ~ push(SingleComparisonNode.Type.GE)
  }

  private def lt: Rule1[SingleComparisonNode.Type] = rule {
    "lt" ~ push(SingleComparisonNode.Type.LT)
  }

  private def le: Rule1[SingleComparisonNode.Type] = rule {
    "le" ~ push(SingleComparisonNode.Type.LE)
  }

  private def like: Rule1[SingleComparisonNode.Type] = rule {
    "like" ~ push(SingleComparisonNode.Type.LIKE)
  }

  private def ilike: Rule1[SingleComparisonNode.Type] = rule {
    "ilike" ~ push(SingleComparisonNode.Type.ILIKE)
  }

  /**
    * MultiComparisonOp          = MultiComparisonName, '(', ComparisonProperty, ',', ComparisonValue, { ',', ComparisonValue }, ')'
    */
  private def MultiComparisonOp: Rule1[Node] = rule {
    MultiComparisonName ~ '(' ~ ComparisonProperty ~ MultiComparisonValues ~ ')' ~>
      ((compType: MultiComparisonNode.Type, compProp: String, compValues: Seq[java.lang.Object]) =>
        new MultiComparisonNode(compType, compProp, CollectionConverters.asJava(compValues.map({
          case None => null
          case default => default
        }))))
  }

  private def MultiComparisonValues: Rule1[immutable.Seq[java.lang.Object]] = rule {
    oneOrMore(',' ~ Literal)
  }

  /**
    * MultiComparisonName        = "in"
    */
  private def MultiComparisonName: Rule1[MultiComparisonNode.Type] = rule {
    in
  }

  private def in: Rule1[MultiComparisonNode.Type] = rule {
    "in" ~ push(MultiComparisonNode.Type.IN)
  }


  /**
    * MultiLogicalOp             = MultiLogicalName, '(', Query, { ',', Query }, ')'
    */
  private def MultiLogicalOp: Rule1[Node] = rule {
    MultiLogicalName ~ '(' ~ oneOrMore(Query).separatedBy(ws(',')) ~ ')' ~>
      ((logicalType: LogicalNode.Type, subQuery: Seq[Node]) =>
        new LogicalNode(logicalType, CollectionConverters.asJava(subQuery)))
  }

  /**
    * MultiLogicalName           = "and" | "or"
    */
  private def MultiLogicalName: Rule1[LogicalNode.Type] = rule {
    and | or
  }

  private def and: Rule1[LogicalNode.Type] = rule {
    "and" ~ push(LogicalNode.Type.AND)
  }

  private def or: Rule1[LogicalNode.Type] = rule {
    "or" ~ push(LogicalNode.Type.OR)
  }

  /**
    * SingleLogicalOp            = SingleLogicalName, '(', Query, ')'
    */
  private def SingleLogicalOp: Rule1[Node] = rule {
    SingleLogicalName ~ '(' ~ Query ~ ')' ~>
      ((logicalType: LogicalNode.Type, subQuery: Node) => new LogicalNode(logicalType, subQuery))
  }

  /**
    * SingleLogicalName          = "not"
    */
  private def SingleLogicalName: Rule1[LogicalNode.Type] = rule {
    not
  }

  private def not: Rule1[LogicalNode.Type] = rule {
    "not" ~ push(LogicalNode.Type.NOT)
  }

  /**
    * ExistsOp                   = "exists" '(', ComparisonProperty, ')'
    */
  private def ExistsOp: Rule1[Node] = rule {
    "exists" ~ '(' ~ ComparisonProperty ~ ')' ~> (property => new ExistsNode(property))
  }


  private def ComparisonProperty: Rule1[java.lang.String] = rule {
    PropertyLiteral
  }

  private def ComparisonValue: Rule1[java.lang.Object] = rule {
    Literal
  }
}

/**
  * Companion singleton Object.
  */
object RqlPredicateParser extends PredicateParser {

  /**
    * Parse the specified input.
    *
    * @param input the input that should be parsed.
    * @return the AST RootNode representing the root of the AST.
    * @throws NullPointerException if input is null.
    * @throws ParserException      if input could not be parsed.
    */
  override def parse(input: String): RootNode = parsePredicate(input)

  private def parsePredicate(string: String): RootNode = {
    val parser = predicateParser(string)
    parser.PredicateRoot.run() match {
      case Success(p) =>
        val rootNode = new RootNode()
        rootNode.getChildren.add(p)
        rootNode
      case Failure(f: ParseError) => throw new ParserException(parser.formatError(f), f)
      case Failure(f) => throw new ParserException("Unknown error during parsing predicate: " + f.getMessage, f)
    }
  }

  private def predicateParser(string: String): RqlPredicateParser = new RqlPredicateParser(ParserInput.apply(string))
}
