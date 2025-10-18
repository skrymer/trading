package com.skrymer.udgaard.logic

/**
 * Example
 *
 * val rule = expr {
 *   ((ref("price") gt 100) and (ref("sector") eq "Tech")) or (ref("changePct") lt -5)
 * }
 * val ok = rule.eval(mapOf("price" to 120, "sector" to "Tech", "changePct" to -2.2))
 */


// ─────────────────────────────── Refs & Literals ──────────────────────────────

/** Top-level helpers (optional to use outside the builder). */
fun ref(path: String) = Operand.VarRef(path)
fun lit(v: Any?) = Operand.Literal(v)

// ─────────────────────── Numeric / Equality infix ops ─────────────────────────

infix fun Operand.VarRef.gt(value: Any?) = Cmp(CmpOp.GT, this, Operand.Literal(value))
infix fun Operand.VarRef.ge(value: Any?) = Cmp(CmpOp.GE, this, Operand.Literal(value))
infix fun Operand.VarRef.lt(value: Any?) = Cmp(CmpOp.LT, this, Operand.Literal(value))
infix fun Operand.VarRef.le(value: Any?) = Cmp(CmpOp.LE, this, Operand.Literal(value))
infix fun Operand.VarRef.eq(value: Any?) = Cmp(CmpOp.EQ, this, Operand.Literal(value))
infix fun Operand.VarRef.ne(value: Any?) = Cmp(CmpOp.NE, this, Operand.Literal(value))

infix fun Operand.VarRef.gt(other: Operand.VarRef) = Cmp(CmpOp.GT, this, other)
infix fun Operand.VarRef.ge(other: Operand.VarRef) = Cmp(CmpOp.GE, this, other)
infix fun Operand.VarRef.lt(other: Operand.VarRef) = Cmp(CmpOp.LT, this, other)
infix fun Operand.VarRef.le(other: Operand.VarRef) = Cmp(CmpOp.LE, this, other)
infix fun Operand.VarRef.eq(other: Operand.VarRef) = Cmp(CmpOp.EQ, this, other)
infix fun Operand.VarRef.ne(other: Operand.VarRef) = Cmp(CmpOp.NE, this, other)

// ───────────────────────────── Date / Time ops ────────────────────────────────

infix fun Operand.VarRef.before(value: Any?) = Cmp(CmpOp.BEFORE, this, Operand.Literal(value))
infix fun Operand.VarRef.after(value: Any?)  = Cmp(CmpOp.AFTER,  this, Operand.Literal(value))

infix fun Operand.VarRef.before(other: Operand.VarRef) = Cmp(CmpOp.BEFORE, this, other)
infix fun Operand.VarRef.after(other: Operand.VarRef)  = Cmp(CmpOp.AFTER,  this, other)

/** Inclusive by default: start ≤ value ≤ end. */
fun Operand.VarRef.between(start: Any?, end: Any?, inclusive: Boolean = true) =
  DateBetween(this, Operand.Literal(start), Operand.Literal(end), inclusive)

/** Between using other refs. */
fun Operand.VarRef.between(start: Operand.VarRef, end: Operand.VarRef, inclusive: Boolean = true) =
  DateBetween(this, start, end, inclusive)

/** Convenient: ref("d").between("2025-01-01" to "2025-12-31") */
infix fun Operand.VarRef.between(range: Pair<Any?, Any?>) =
  DateBetween(this, Operand.Literal(range.first), Operand.Literal(range.second), true)

// Optional sugar
infix fun Operand.VarRef.onOrBefore(value: Any?) = !(this after value)
infix fun Operand.VarRef.onOrAfter(value: Any?)  = !(this before value)

// ───────────────────────── Boolean combinators ────────────────────────────────

infix fun Expression.and(other: Expression): Expression =
  when {
    this is And && other is And -> And(this.args + other.args)
    this is And -> And(this.args + other)
    other is And -> And(listOf(this) + other.args)
    else -> And(listOf(this, other))
  }

infix fun Expression.or(other: Expression): Expression =
  when {
    this is Or && other is Or -> Or(this.args + other.args)
    this is Or -> Or(this.args + other)
    other is Or -> Or(listOf(this) + other.args)
    else -> Or(listOf(this, other))
  }

operator fun Expression.not(): Expression = Not(this)

/** Var-arg helpers */
fun allOf(vararg exprs: Expression): Expression =
  exprs.reduce { acc, e -> acc and e }

fun anyOf(vararg exprs: Expression): Expression =
  exprs.reduce { acc, e -> acc or e }

fun noneOf(vararg exprs: Expression): Expression = !anyOf(*exprs)

// ─────────────────────────────── Builder block ────────────────────────────────

/** Write: val rule = expr { (ref("age") ge 18) and (ref("age") lt 65) } */
fun expr(block: ExprDsl.() -> Expression): Expression = ExprDsl().block()

class ExprDsl {
  /** Use these inside `expr { ... }` without any package qualifier. */
  fun ref(path: String) = Operand.VarRef(path)
  fun lit(v: Any?) = Operand.Literal(v)
}

// ───────────────────────── JSON serialization ─────────────────────────────────

fun Expression.toJson(pretty: Boolean = false): String {
  val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
  val node = this.toJsonNode(mapper)
  return if (pretty) mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
  else mapper.writeValueAsString(node)
}

private fun Expression.toJsonNode(mapper: com.fasterxml.jackson.databind.ObjectMapper)
  : com.fasterxml.jackson.databind.node.ObjectNode {
  val obj = mapper.createObjectNode()
  when (this) {
    is And -> {
      obj.put("op", "and")
      val arr = mapper.createArrayNode()
      args.forEach { arr.add(it.toJsonNode(mapper)) }
      obj.set<com.fasterxml.jackson.databind.JsonNode>("args", arr)
    }
    is Or -> {
      obj.put("op", "or")
      val arr = mapper.createArrayNode()
      args.forEach { arr.add(it.toJsonNode(mapper)) }
      obj.set<com.fasterxml.jackson.databind.JsonNode>("args", arr)
    }
    is Not -> {
      obj.put("op", "not")
      val arr = mapper.createArrayNode()
      arr.add(arg.toJsonNode(mapper))
      obj.set<com.fasterxml.jackson.databind.JsonNode>("args", arr)
    }
    is Cmp -> {
      obj.put("op", when (op) {
        CmpOp.GT -> "gt"; CmpOp.GE -> "ge"; CmpOp.LT -> "lt"; CmpOp.LE -> "le"
        CmpOp.EQ -> "eq"; CmpOp.NE -> "ne"; CmpOp.BEFORE -> "before"; CmpOp.AFTER -> "after"
      })
      obj.set<com.fasterxml.jackson.databind.JsonNode>("left", left.toJsonNode(mapper))
      obj.set<com.fasterxml.jackson.databind.JsonNode>("right", right.toJsonNode(mapper))
    }
    is DateBetween -> {
      obj.put("op", "between")
      obj.set<com.fasterxml.jackson.databind.JsonNode>("value", value.toJsonNode(mapper))
      obj.set<com.fasterxml.jackson.databind.JsonNode>("start", start.toJsonNode(mapper))
      obj.set<com.fasterxml.jackson.databind.JsonNode>("end",   end.toJsonNode(mapper))
      obj.put("inclusive", inclusive)
    }
  }
  return obj
}

private fun Operand.toJsonNode(mapper: com.fasterxml.jackson.databind.ObjectMapper)
  : com.fasterxml.jackson.databind.JsonNode =
  when (this) {
    is Operand.VarRef -> mapper.createObjectNode().put("var", path)
    is Operand.Literal -> mapper.valueToTree(this.v)
  }
