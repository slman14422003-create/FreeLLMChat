package com.freellm.chat.data

import org.mariuszgromada.math.mxparser.Argument
import org.mariuszgromada.math.mxparser.Expression
import java.math.BigDecimal
import java.math.MathContext
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object MathEngine {
    private class Out(val value: Double?, val error: String)

    private val pool = Executors.newFixedThreadPool(2) { r -> Thread(r, "math").apply { isDaemon = true } }
    private val splitter = Regex("[;\\n]|,(?=\\s*[A-Za-z_]\\w*\\s*=)")

    fun format(v: Double): String = when {
        v.isNaN() -> "undefined (NaN)"
        v.isInfinite() -> if (v > 0) "infinity" else "-infinity"
        v == Math.rint(v) && Math.abs(v) < 1e15 -> v.toLong().toString()
        Math.abs(v) in 1e-6..1e15 -> BigDecimal(v).round(MathContext(12)).stripTrailingZeros().toPlainString()
        else -> v.toString()
    }

    fun eval(expression: String, vars: String = "", timeoutMs: Long = 5000): String {
        val src = expression.trim()
        if (src.isEmpty()) return "Empty expression."
        val task = Callable {
            val e = Expression(src)
            vars.split(splitter).map { it.trim() }.filter { it.isNotEmpty() && it.contains('=') }.forEach { e.addArguments(Argument(it)) }
            if (!e.checkSyntax()) Out(null, e.errorMessage.orEmpty().trim()) else Out(e.calculate(), "")
        }
        val f = pool.submit(task)
        val out = try {
            f.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            f.cancel(true)
            return "Calculation timed out. Use smaller ranges."
        } catch (e: ExecutionException) {
            return "Math error: " + (e.cause?.message ?: e.message).orEmpty().take(120)
        } catch (e: InterruptedException) {
            f.cancel(true)
            throw e
        }
        val v = out.value ?: return "Syntax error: " + out.error.replace('\n', ' ').take(200)
        return format(v)
    }
}
