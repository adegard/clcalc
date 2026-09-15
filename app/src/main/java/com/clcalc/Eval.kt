package com.clcalc

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

class EvalException(message: String) : Exception(message)

private sealed class Tok
private object LPar : Tok()
private object RPar : Tok()
private object Comma : Tok()
private data class NumTok(val v: Double) : Tok()
private data class OpTok(val c: Char) : Tok()
private data class IdTok(val name: String) : Tok()

object Eval {

    var ans = Double.NaN

    fun evaluate(input: String): String {
        val toks = tokenize(input)
        if (toks.isEmpty()) return ""
        val p = Parser(toks)
        val v = p.parseExpression()
        p.checkEnd()
        ans = v
        return format(v)
    }

    // ------------------------------------------------------------------ tokens

    private fun tokenize(src: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        val n = src.length
        val isIdentStart: (Char) -> Boolean = { ch -> ch.isLetter() || ch == '_' }
        val isIdentPart: (Char) -> Boolean = { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '$' }

        while (i < n) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> { out.add(LPar); i++ }
                c == ')' -> { out.add(RPar); i++ }
                c == ',' -> { out.add(Comma); i++ }
                c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '^' || c == '!' -> {
                    out.add(OpTok(c)); i++
                }
                c == '×' -> { out.add(OpTok('*')); i++ }
                c == '÷' -> { out.add(OpTok('/')); i++ }
                c == '−' -> { out.add(OpTok('-')); i++ }
                c == 'π' -> { out.add(IdTok("pi")); i++ }
                c.isDigit() || (c == '.' && i + 1 < n && src[i + 1].isDigit()) -> {
                    var j = i
                    var dotSeen = false
                    var expSeen = false
                    while (j < n) {
                        val ch = src[j]
                        if (ch.isDigit()) j++
                        else if (ch == '.' && !dotSeen && !expSeen) { dotSeen = true; j++ }
                        else if ((ch == 'e' || ch == 'E') && !expSeen &&
                                 (j + 1 < n && (src[j + 1].isDigit() ||
                                     ((src[j + 1] == '+' || src[j + 1] == '-') && j + 2 < n && src[j + 2].isDigit())))) {
                            expSeen = true; j++
                            if (src[j] == '+' || src[j] == '-') j++
                        } else break
                    }
                    out.add(NumTok(src.substring(i, j).toDoubleOrNull() ?: throw EvalException("Bad number")))
                    i = j
                }
                isIdentStart(c) -> {
                    var j = i
                    while (j < n && isIdentPart(src[j])) j++
                    out.add(IdTok(src.substring(i, j)))
                    i = j
                }
                else -> throw EvalException("Unexpected character '$c'")
            }
        }
        return out
    }

    // ------------------------------------------------------------------ parser

    private class Parser(private val toks: List<Tok>) {
        private var pos = 0
        private fun peek(step: Int = 0): Tok? = toks.getOrNull(pos + step)
        private fun next(): Tok = toks[pos++]

        private fun isOperandStart(t: Tok?): Boolean =
            t is NumTok || t is IdTok || t == LPar

        fun checkEnd() {
            if (pos < toks.size) throw EvalException("Unexpected '${render(toks[pos])}'")
        }

        // expr := term (('+' | '-') term | adjacency)*
        fun parseExpression(): Double {
            var v = parseTerm().value
            while (true) {
                when (val t = peek()) {
                    is OpTok -> when (t.c) {
                        '+' -> {
                            next()
                            val r = parseTerm()
                            v += if (r.topPct) v * r.value else r.value
                        }
                        '-' -> {
                            next()
                            val r = parseTerm()
                            v -= if (r.topPct) v * r.value else r.value
                        }
                        else -> break
                    }
                    else -> {
                        if (isOperandStart(t)) v *= parseTerm().value else break
                    }
                }
            }
            return v
        }

        // term := factor (('*' | '/' ) factor | adjacency)*, factor may take % and !
        // tracks whether a trailing '%' was applied to the leading factor without any
        // following * / (i.e. term is just "b%"), which lets parseExpression turn
        // "a + b%" into "a + a*b/100".
        private data class TermResult(val value: Double, val topPct: Boolean)

        private fun parseTerm(): TermResult {
            var v = parseFactor()
            var pctStep = false // last consumed op was a trailing '%' with no * / before it
            loop@ while (true) {
                when (val t = peek()) {
                    is OpTok -> when (t.c) {
                        '%' -> { next(); v /= 100.0; pctStep = true }
                        '*' -> { next(); pctStep = false; v *= parseFactorPct() }
                        '/' -> {
                            next(); pctStep = false
                            val d = parseFactorPct()
                            if (d == 0.0) throw EvalException("Division by zero")
                            v /= d
                        }
                        else -> break@loop
                    }
                    else -> {
                        if (isOperandStart(t)) { pctStep = false; v *= parseFactorPct() } else break@loop
                    }
                }
            }
            return TermResult(v, pctStep)
        }

        // like parseFactor but a trailing '%' divides just this factor by 100
        private fun parseFactorPct(): Double {
            var f = parseFactor()
            if (peek() is OpTok && (peek() as OpTok).c == '%') { next(); f /= 100.0 }
            return f
        }

        private fun parseFactor(): Double {
            var v: Double
            when (val t = peek()) {
                is OpTok -> if (t.c == '-') { next(); v = -parseFactor() } else if (t.c == '+') { next(); v = parseFactor() } else throw EvalException("Unexpected '${render(t)}'")
                else -> v = parsePower()
            }
            // postfix: factorial
            while (true) {
                when (val p = peek()) {
                    is OpTok -> if (p.c == '!') { next(); v = factorial(v) } else break
                    else -> break
                }
            }
            return v
        }

        // power := primary ('^' unary)?   (right associative, ^ binds tighter than unary minus)
        private fun parsePower(): Double {
            val base = parsePrimary()
            if (peek() is OpTok && (peek() as OpTok).c == '^') {
                next()
                return base.pow(parsePower())
            }
            return base
        }

        private fun parsePrimary(): Double {
            val t = next()
            return when (t) {
                is NumTok -> t.v
                LPar -> {
                    val v = parseExpression()
                    val r = next()
                    if (r != RPar) throw EvalException("Missing ')'")
                    v
                }
                is IdTok -> {
                    if (peek() == LPar) callFunction(t.name.lowercase()) else constant(t.name.lowercase())
                }
                else -> throw EvalException("Unexpected '${render(t)}'")
            }
        }

        private fun callFunction(name: String): Double {
            next() // consume '('
            val args = mutableListOf<Double>()
            if (peek() != RPar) {
                args.add(parseExpression())
                while (peek() == Comma) { next(); args.add(parseExpression()) }
            }
            val r = next()
            if (r != RPar) throw EvalException("Missing ')' in function $name")
            return applyFunction(name, args)
        }

        private fun constant(name: String): Double = when (name) {
            "pi" -> Math.PI
            "e" -> Math.E
            "phi" -> (1.0 + sqrt(5.0)) / 2.0
            "tau" -> 2.0 * Math.PI
            "ans" -> if (ans.isNaN()) throw EvalException("'ans' not available yet") else ans
            else -> throw EvalException("Unknown name '$name'")
        }

        private fun need(name: String, args: List<Double>, min: Int, max: Int = min) {
            if (args.size < min || args.size > max) {
                val expect = if (min == max) "$min" else "$min-$max"
                throw EvalException("$name() expects $expect argument(s), got ${args.size}")
            }
        }

        private fun one(name: String, args: List<Double>): Double = args[0]

        private fun applyFunction(name: String, args: List<Double>): Double = when (name) {
            "sqrt" -> need(name, args, 1).run { sqrt(one(name, args)) }
            "cbrt" -> need(name, args, 1).run { Math.cbrt(one(name, args)) }
            "abs" -> need(name, args, 1).run { abs(one(name, args)) }
            "sign" -> need(name, args, 1).run { val a = one(name, args); if (a > 0) 1.0 else if (a < 0) -1.0 else 0.0 }
            "round" -> need(name, args, 1).run { floor(one(name, args) + 0.5) }
            "floor" -> need(name, args, 1).run { floor(one(name, args)) }
            "ceil" -> need(name, args, 1).run { ceil(one(name, args)) }
            "trunc" -> need(name, args, 1).run { val a = one(name, args); if (a < 0) ceil(a) else floor(a) }
            "frac" -> need(name, args, 1).run { one(name, args) - floor(one(name, args)) }
            "exp" -> need(name, args, 1).run { exp(one(name, args)) }
            "ln" -> need(name, args, 1).run { ln(one(name, args)) }
            "log" -> {
                when {
                    args.size == 1 -> log10(args[0])
                    args.size == 2 -> ln(args[0]) / ln(args[1])
                    else -> throw EvalException("log() expects 1 or 2 argument(s), got ${args.size}")
                }
            }
            "log2" -> need(name, args, 1).run { log2(one(name, args)) }
            "pow" -> need(name, args, 2).run { args[0].pow(args[1]) }
            "hypot" -> need(name, args, 2).run { hypot(args[0], args[1]) }
            "atan2" -> need(name, args, 2).run { atan2(args[0], args[1]) }
            "min" -> need(name, args, 1, Int.MAX_VALUE).run { requireNotNull(args.minOrNull()) }
            "max" -> need(name, args, 1, Int.MAX_VALUE).run { requireNotNull(args.maxOrNull()) }
            "mod" -> need(name, args, 2).run { args[0] % args[1] }
            "gcd" -> need(name, args, 2).run { gcd(args[0].toLong(), args[1].toLong()).toDouble() }
            "lcm" -> need(name, args, 2).run { lcm(args[0].toLong(), args[1].toLong()).toDouble() }
            "fact" -> need(name, args, 1).run { factorial(one(name, args)) }
            "sin" -> need(name, args, 1).run { sin(one(name, args)) }
            "cos" -> need(name, args, 1).run { cos(one(name, args)) }
            "tan" -> need(name, args, 1).run { tan(one(name, args)) }
            "asin" -> need(name, args, 1).run { asin(one(name, args)) }
            "acos" -> need(name, args, 1).run { acos(one(name, args)) }
            "atan" -> need(name, args, 1).run { atan(one(name, args)) }
            "sec" -> need(name, args, 1).run { 1.0 / cos(one(name, args)) }
            "csc" -> need(name, args, 1).run { 1.0 / sin(one(name, args)) }
            "cot" -> need(name, args, 1).run { 1.0 / tan(one(name, args)) }
            "rand" -> when (args.size) {
                0 -> Random.nextDouble()
                1 -> Random.nextDouble() * args[0]
                2 -> if (args[1] <= args[0]) throw EvalException("rand(lo,hi): hi must be > lo") else args[0] + Random.nextDouble() * (args[1] - args[0])
                else -> throw EvalException("rand() expects 0, 1 or 2 argument(s), got ${args.size}")
            }
            else -> throw EvalException("Unknown function '$name'")
        }

        private fun render(t: Tok): String = when (t) {
            is NumTok -> "number"
            is OpTok -> "'${t.c}'"
            is IdTok -> "'${t.name}'"
            LPar -> "'('"
            RPar -> "')'"
            Comma -> "','"
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun factorial(v: Double): Double {
        if (v < 0 || v != floor(v)) throw EvalException("Factorial needs a non-negative integer")
        if (v > 170) throw EvalException("Factorial too large")
        var r = 1.0
        var i = 2.0
        while (i <= v) { r *= i; i++ }
        return r
    }

    private fun gcd(a: Long, b: Long): Long {
        var x = abs(a)
        var y = abs(b)
        while (y != 0L) { val t = x % y; x = y; y = t }
        return x
    }

    private fun lcm(a: Long, b: Long): Long {
        if (a == 0L || b == 0L) return 0L
        return abs(a / gcd(a, b) * b)
    }

    fun format(v: Double): String {
        if (v.isNaN()) return "NaN"
        if (v.isInfinite()) return if (v > 0) "Infinity" else "-Infinity"
        val a = abs(v)
        return if (a != 0.0 && (a < 1e-9 || a >= 1e15)) {
            String.format("%.8e", v).replace("e+0", "e+").replace("e-0", "e-")
        } else {
            var s = String.format("%.12f", v)
            if (s.contains('.')) {
                s = s.trimEnd('0').trimEnd('.')
                if (s == "-0") s = "0"
            }
            s
        }
    }
}