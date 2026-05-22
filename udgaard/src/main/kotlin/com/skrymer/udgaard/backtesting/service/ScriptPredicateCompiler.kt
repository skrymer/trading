package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

/** A compiled entry-condition predicate: a pure function of the current bar. */
typealias EntryPredicate = (Stock, StockQuote, BacktestContext) -> Boolean

/** A compiled exit-condition predicate: a pure function of the entry/current bars + breadth context. */
typealias ExitPredicate = (Stock, StockQuote?, StockQuote, BacktestContext) -> Boolean

/**
 * Compiles user-supplied Kotlin scripts into entry/exit-condition predicates.
 *
 * Each distinct script is wrapped in a typed lambda and compiled to JVM bytecode once via the
 * Kotlin scripting host; the resulting predicate is a plain function object called per bar at
 * native speed — the scripting engine is never touched during evaluation. Compiled predicates
 * are cached process-wide by script text, so a script reused across many backtests (e.g.
 * walk-forward windows) compiles only once. The typed wrapper makes a script that does not
 * yield a `Boolean` a compile error rather than a per-bar failure.
 *
 * The compilation classpath is resolved from this class's classloader so scripts can reference
 * app domain types (`Stock`, `StockQuote`, `BacktestContext`). NOTE: this resolution only works
 * over a plain classpath of real jar files — the app must run exploded, not via `java -jar`
 * (Spring Boot's nested-jar classloader hides the classpath, incl. the Kotlin stdlib). See the
 * udgaard Dockerfile.
 */
@Component
class ScriptPredicateCompiler {
  private val host = BasicJvmScriptingHost()

  private val compilationConfig =
    ScriptCompilationConfiguration {
      jvm {
        dependenciesFromClassContext(ScriptPredicateCompiler::class, wholeClasspath = true)
      }
    }

  private val entryCache = ConcurrentHashMap<String, EntryPredicate>()
  private val exitCache = ConcurrentHashMap<String, ExitPredicate>()

  /**
   * Compile [script] — a Kotlin expression over `stock`, `quote`, `context` — into a predicate.
   * Throws [IllegalArgumentException] with the compiler diagnostics if the script does not
   * compile, or does not yield a `Boolean`.
   */
  fun compileEntry(script: String): EntryPredicate =
    entryCache.computeIfAbsent(script) {
      @Suppress("UNCHECKED_CAST")
      compile(
        "val __p: (Stock, StockQuote, BacktestContext) -> Boolean = { stock, quote, context ->\n$it\n}\n__p",
        "Entry",
      ) as EntryPredicate
    }

  /**
   * Compile [script] — a Kotlin expression over `stock`, `entryQuote`, `quote`, `context` —
   * into a predicate. Throws [IllegalArgumentException] as [compileEntry] does.
   */
  fun compileExit(script: String): ExitPredicate =
    exitCache.computeIfAbsent(script) {
      @Suppress("UNCHECKED_CAST")
      compile(
        "val __p: (Stock, StockQuote?, StockQuote, BacktestContext) -> Boolean = " +
          "{ stock, entryQuote, quote, context ->\n$it\n}\n__p",
        "Exit",
      ) as ExitPredicate
    }

  private fun compile(lambdaDeclaration: String, kind: String): Any {
    val wrapped =
      "import com.skrymer.udgaard.data.model.*\n" +
        "import com.skrymer.udgaard.backtesting.model.*\n" +
        lambdaDeclaration

    val result = synchronized(host) { host.eval(wrapped.toScriptSource(), compilationConfig, null) }

    return when (result) {
      is ResultWithDiagnostics.Success -> extractPredicate(result, kind)
      is ResultWithDiagnostics.Failure ->
        throw IllegalArgumentException(
          "$kind condition script failed to compile:\n" +
            result.reports
              .filter { it.severity >= ScriptDiagnostic.Severity.ERROR }
              .joinToString("\n") { it.message },
        )
    }
  }

  /** Pull the compiled predicate lambda out of a successful script evaluation. */
  private fun extractPredicate(result: ResultWithDiagnostics.Success<EvaluationResult>, kind: String): Any {
    val returnValue = result.value.returnValue
    check(returnValue is ResultValue.Value) {
      "$kind condition script did not yield a predicate: $returnValue"
    }
    return returnValue.value ?: throw IllegalStateException("$kind condition script yielded null")
  }
}
