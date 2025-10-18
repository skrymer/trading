package com.skrymer.udgaard.logic

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeParseException
import java.util.Date

/**
 * Example
 *
 * val exprJson = """
 * {
 *   "op": "or",
 *   "args": [
 *     {
 *       "op": "and",
 *       "args": [
 *         { "op": "gt", "left": { "var": "price" }, "right": 100 },
 *         { "op": "eq", "left": { "var": "sector" }, "right": "Tech" }
 *       ]
 *     },
 *     { "op": "lt", "left": { "var": "changePct" }, "right": -5 }
 *   ]
 * }
 * """.trimIndent()
 *
 * val ctx = mapOf(
 *   "price" to 120,
 *   "sector" to "Tech",
 *   "changePct" to -2.2
 * )
 *
 * val ok = evaluateExpression(exprJson, ctx)   // true
 */

// ---------- AST ----------
sealed interface Expression {
  /** Backwards compatible eval (uses system default zone for date ops). */
  fun eval(ctx: Map<String, Any?>): Boolean = eval(ctx, ZoneId.systemDefault())

  /** Preferred eval letting you specify the zone for date-only coercions. */
  fun eval(ctx: Map<String, Any?>, zone: ZoneId): Boolean
}

sealed interface Operand {
  fun value(ctx: Map<String, Any?>): Any? = null

  data class VarRef(val path: String) : Operand {
    override fun value(ctx: Map<String, Any?>): Any? = resolvePath(ctx, path)
  }
  data class Literal(val v: Any?) : Operand {
    override fun value(ctx: Map<String, Any?>): Any? = v
  }
}

data class And(val args: List<Expression>) : Expression {
  override fun eval(ctx: Map<String, Any?>, zone: ZoneId): Boolean {
    for (e in args) if (!e.eval(ctx, zone)) return false
    return true
  }
}
data class Or(val args: List<Expression>) : Expression {
  override fun eval(ctx: Map<String, Any?>, zone: ZoneId): Boolean {
    for (e in args) if (e.eval(ctx, zone)) return true
    return false
  }
}
data class Not(val arg: Expression) : Expression {
  override fun eval(ctx: Map<String, Any?>, zone: ZoneId): Boolean = !arg.eval(ctx, zone)
}

enum class CmpOp { GT, GE, LT, LE, EQ, NE, BEFORE, AFTER }

data class Cmp(val op: CmpOp, val left: Operand, val right: Operand) : Expression {
  override fun eval(ctx: Map<String, Any?>, zone: ZoneId): Boolean {
    val l = left.value(ctx)
    val r = right.value(ctx)

    return when (op) {
      CmpOp.GT -> compareNumbers(l, r)?.let { it > 0 } ?: false
      CmpOp.GE -> compareNumbers(l, r)?.let { it >= 0 } ?: false
      CmpOp.LT -> compareNumbers(l, r)?.let { it < 0 } ?: false
      CmpOp.LE -> compareNumbers(l, r)?.let { it <= 0 } ?: false
      CmpOp.EQ -> equalsNormalized(l, r)
      CmpOp.NE -> !equalsNormalized(l, r)
      CmpOp.BEFORE -> compareTemporal(l, r, zone)?.let { it < 0 } ?: false
      CmpOp.AFTER  -> compareTemporal(l, r, zone)?.let { it > 0 } ?: false
    }
  }
}

/** Date/time: value BETWEEN [start, end] (inclusive by default). */
data class DateBetween(
  val value: Operand,
  val start: Operand,
  val end: Operand,
  val inclusive: Boolean = true
) : Expression {
  override fun eval(ctx: Map<String, Any?>, zone: ZoneId): Boolean {
    val v = value.value(ctx); val s = start.value(ctx); val e = end.value(ctx)
    val vs = compareTemporal(v, s, zone) ?: return false
    val ve = compareTemporal(v, e, zone) ?: return false
    return if (inclusive) (vs >= 0 && ve <= 0) else (vs > 0 && ve < 0)
  }
}

// ---------- Parser ----------
object Logic {
  private val M = jacksonObjectMapper()

  fun parse(json: String): Expression = parse(M.readTree(json))

  fun parse(node: JsonNode): Expression {
    require(node.isObject) { "Expression must be an object. Got: $node" }
    val op = node["op"]?.asText() ?: error("Missing 'op' property in $node")

    return when (op) {
      "and", "or" -> {
        val argsNode = node["args"] ?: error("Missing 'args' for $op")
        require(argsNode.isArray && argsNode.size() >= 2) { "'$op' requires >= 2 args" }
        val args = argsNode.map { parse(it) }
        if (op == "and") And(args) else Or(args)
      }
      "not" -> {
        val argsNode = node["args"] ?: error("Missing 'args' for not")
        require(argsNode.isArray && argsNode.size() == 1) { "'not' requires exactly 1 arg" }
        Not(parse(argsNode[0]))
      }

      // Numeric & equality
      "gt", "ge", "lt", "le", "eq", "ne" -> {
        val left = parseOperand(node["left"] ?: error("Missing 'left' for $op"))
        val right = parseOperand(node["right"] ?: error("Missing 'right' for $op"))
        Cmp(
          when (op) {
            "gt" -> CmpOp.GT; "ge" -> CmpOp.GE; "lt" -> CmpOp.LT
            "le" -> CmpOp.LE; "eq" -> CmpOp.EQ; "ne" -> CmpOp.NE
            else -> error("unexpected")
          }, left, right
        )
      }

      // Date/time single-side ops
      "before", "after" -> {
        val left = parseOperand(node["left"] ?: error("Missing 'left' for $op"))
        val right = parseOperand(node["right"] ?: error("Missing 'right' for $op"))
        Cmp(if (op == "before") CmpOp.BEFORE else CmpOp.AFTER, left, right)
      }

      // Date/time between (supports two shapes: value/start/end OR args[3])
      "between" -> {
        val hasNamed = node.has("value") && node.has("start") && node.has("end")
        val (value, start, end) =
          if (hasNamed) {
            Triple(parseOperand(node["value"]), parseOperand(node["start"]), parseOperand(node["end"]))
          } else {
            val args = node["args"] ?: error("Missing 'value/start/end' or 'args' for between")
            require(args.isArray && args.size() == 3) { "'between' requires 3 args: [value, start, end]" }
            Triple(parseOperand(args[0]), parseOperand(args[1]), parseOperand(args[2]))
          }
        val inclusive = node["inclusive"]?.asBoolean() ?: true
        DateBetween(value, start, end, inclusive)
      }

      else -> error("Unknown op: '$op'")
    }
  }

  private fun parseOperand(n: JsonNode): Operand {
    if (n.isObject && n.has("var")) {
      val p = n["var"].asText()
      require(p.isNotBlank()) { "'var' must be non-blank" }
      return Operand.VarRef(p)
    }
    return Operand.Literal(jsonLiteral(n))
  }

  private fun jsonLiteral(n: JsonNode): Any? = when {
    n.isNull -> null
    n.isTextual -> n.asText()
    n.isBoolean -> n.asBoolean()
    n.isNumber -> n.decimalValue() // BigDecimal
    n.isArray -> n.map { jsonLiteral(it) }
    n.isObject -> n.fields().asSequence().associate { it.key to jsonLiteral(it.value) }
    else -> error("Unsupported literal node: $n")
  }
}

// ---------- Helpers (equality & numeric) ----------
private fun equalsNormalized(a: Any?, b: Any?): Boolean {
  val an = (a as? Number)?.toBigDecimalOrNull() ?: a
  val bn = (b as? Number)?.toBigDecimalOrNull() ?: b
  return when {
    an is BigDecimal && bn is BigDecimal -> an.compareTo(bn) == 0
    else -> an == bn
  }
}

private fun compareNumbers(a: Any?, b: Any?): Int? {
  val ad = a.toBigDecimalOrNull() ?: return null
  val bd = b.toBigDecimalOrNull() ?: return null
  return ad.compareTo(bd)
}

private fun Any?.toBigDecimalOrNull(): BigDecimal? = when (this) {
  is BigDecimal -> this
  is Byte -> toLong().toBigDecimal()
  is Short -> toLong().toBigDecimal()
  is Int -> toLong().toBigDecimal()
  is Long -> toBigDecimal()
  is Float -> toString().toBigDecimalOrNull()
  is Double -> toString().toBigDecimalOrNull()
  is String -> this.toBigDecimalOrNull()
  else -> null
}

// ---------- Date/time normalization ----------
private sealed interface TemporalLike {
  data class InstantLike(val v: Instant) : TemporalLike
  data class DateOnly(val d: LocalDate) : TemporalLike
}

/**
 * Returns negative if a < b, zero if equal, positive if a > b.
 * Chooses comparison domain:
 * - If either side is date-only -> compare as LocalDate (convert date-times via zone)
 * - Else compare as Instant
 */
private fun compareTemporal(a: Any?, b: Any?, zone: ZoneId): Int? {
  val ta = toTemporal(a, zone) ?: return null
  val tb = toTemporal(b, zone) ?: return null
  return when {
    ta is TemporalLike.DateOnly || tb is TemporalLike.DateOnly -> {
      val da = asLocalDate(ta, zone)
      val db = asLocalDate(tb, zone)
      da.compareTo(db)
    }
    else -> {
      val ia = (ta as TemporalLike.InstantLike).v
      val ib = (tb as TemporalLike.InstantLike).v
      ia.compareTo(ib)
    }
  }
}

private fun asLocalDate(t: TemporalLike, zone: ZoneId): LocalDate = when (t) {
  is TemporalLike.DateOnly -> t.d
  is TemporalLike.InstantLike -> t.v.atZone(zone).toLocalDate()
}

private fun toTemporal(value: Any?, zone: ZoneId): TemporalLike? = when (value) {
  null -> null
  is TemporalLike -> value
  is Instant -> TemporalLike.InstantLike(value)
  is LocalDate -> TemporalLike.DateOnly(value)
  is LocalDateTime -> TemporalLike.InstantLike(value.atZone(zone).toInstant())
  is OffsetDateTime -> TemporalLike.InstantLike(value.toInstant())
  is ZonedDateTime -> TemporalLike.InstantLike(value.toInstant())
  is Date -> TemporalLike.InstantLike(value.toInstant())
  is Number -> TemporalLike.InstantLike(Instant.ofEpochMilli(value.toLong()))
  is CharSequence -> parseDateLike(value.toString(), zone)
  else -> null
}

private fun parseDateLike(s: String, zone: ZoneId): TemporalLike? {
  // Fast path: epoch millis in string
  s.toLongOrNull()?.let { return TemporalLike.InstantLike(Instant.ofEpochMilli(it)) }

  // Try instant-like (with zone/offset or 'Z')
  fun tryInstantParsers(): TemporalLike? = try {
    when {
      looksLikeInstant(s) -> TemporalLike.InstantLike(Instant.parse(s))
      looksLikeOffsetDateTime(s) -> TemporalLike.InstantLike(OffsetDateTime.parse(s).toInstant())
      looksLikeZonedDateTime(s) -> TemporalLike.InstantLike(ZonedDateTime.parse(s).toInstant())
      looksLikeLocalDateTime(s) -> TemporalLike.InstantLike(LocalDateTime.parse(s).atZone(zone).toInstant())
      else -> null
    }
  } catch (_: DateTimeParseException) { null }

  // Try date-only
  fun tryDateOnly(): TemporalLike? = try {
    TemporalLike.DateOnly(LocalDate.parse(s))
  } catch (_: DateTimeParseException) { null }

  return tryInstantParsers() ?: tryDateOnly()
}

private fun looksLikeInstant(s: String) = s.endsWith("Z") && s.contains('T')
private fun looksLikeOffsetDateTime(s: String) =
  s.contains('T') && (s.contains('+') || s.contains('-') && s.lastIndexOf('-') > 9 && s.substringAfterLast('-').contains(':'))
private fun looksLikeZonedDateTime(s: String) = s.contains('[') && s.contains(']')
private fun looksLikeLocalDateTime(s: String) = s.contains('T')

// ---------- Var resolution ----------
private fun resolvePath(root: Any?, path: String): Any? {
  if (root !is Map<*, *>) return null
  var cur: Any? = root
  for (raw in path.split('.')) {
    val key = raw.trim()
    cur = when (val c = cur) {            // bind to immutable val
      null -> return null
      is Map<*, *> -> c[key]
      is List<*> -> {
        val idx = key.toIntOrNull() ?: return null
        c.getOrNull(idx)                  // safe, no lambda capture
      }
      else -> return null
    }
  }
  return cur
}


// ---------- Convenience API ----------
fun evaluateExpression(json: String, context: Map<String, Any?>): Boolean =
  Logic.parse(json).eval(context)

/** Overload to control the zone used when coercing date-times -> LocalDate. */
fun evaluateExpression(json: String, context: Map<String, Any?>, zone: ZoneId): Boolean =
  Logic.parse(json).eval(context, zone)
