package com.vijayrc.kabali

/**
  * Created by vxr63 on 6/17/16.
  */
import scala.util.parsing.combinator.{JavaTokenParsers, PackratParsers}

sealed abstract class AST
sealed abstract class BooleanExpression extends AST
case class BooleanOperation(op: String, lhs: BooleanExpression, rhs:BooleanExpression) extends BooleanExpression
case class Comparison(op:String, rhs:Constant) extends BooleanExpression
case class Constant(value: Double) extends AST

object ConditionParser extends JavaTokenParsers with PackratParsers {

  val booleanOperator : PackratParser[String] = literal("||") | literal("&&")
  val comparisonOperator : PackratParser[String] = literal("<=") | literal(">=") | literal("==") | literal("!=") | literal("<") | literal(">")
  val constant : PackratParser[Constant] = floatingPointNumber.^^ { x => Constant(x.toDouble) }
  val comparison : PackratParser[Comparison] = (comparisonOperator ~ constant) ^^ { case op ~ rhs => Comparison(op, rhs) }
  lazy val p1 : PackratParser[BooleanExpression] = booleanOperation | comparison
  val booleanOperation = (p1 ~ booleanOperator ~ p1) ^^ { case lhs ~ op ~ rhs => BooleanOperation(op, lhs, rhs) }
}

object Evaluator {

  def evaluate(expression:BooleanExpression, value:Double) : Boolean = expression match {
    case Comparison("<=", Constant(c)) => value <= c
    case Comparison(">=", Constant(c)) => value >= c
    case Comparison("==", Constant(c)) => value == c
    case Comparison("!=", Constant(c)) => value != c
    case Comparison("<", Constant(c)) => value < c
    case Comparison(">", Constant(c)) => value > c
    case BooleanOperation("||", a, b) => evaluate(a, value) || evaluate(b, value)
    case BooleanOperation("&&", a, b) => evaluate(a, value) && evaluate(b, value)
  }
}

object Test extends App {

  def parse(text:String) : BooleanExpression = ConditionParser.parseAll(ConditionParser.p1, text).get

  val texts = Seq(
    "<2000",
    "<2000||>20000",
    "==1",
    "<=3000",
    ">0.9&&<1.1")

  val xs = Seq(0.0, 1.0, 100000.0)

  for {
    text <- texts
    expression = parse(text)
    x <- xs
    result = Evaluator.evaluate(expression, x)
  } {
    println(s"$text $expression $x $result")
  }
}
