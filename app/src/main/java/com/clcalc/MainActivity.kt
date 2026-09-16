package com.clcalc

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var output: TextView
    private lateinit var prompt: TextView
    private lateinit var input: EditText
    private lateinit var title: TextView

    private val tinted = mutableListOf<Button>()

    private var dark = true
    private val history = mutableListOf<String>()
    private var histIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        applyTheme()
        writeLine("CL Calc v1.0 - OLED terminal calculator")
        writeLine("type 'help' for help, 'clear' to clear the screen")
        writeLine("")
        input.requestFocus()
    }

    // ------------------------------------------------------------------ UI

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(buildHeader())

        scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            isVerticalScrollBarEnabled = false
        }
        output = TextView(this).apply {
            textSize = 15f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.05f)
            isVerticalScrollBarEnabled = false
        }
        scroll.addView(output, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(scroll)
        root.addView(buildInputRow())
        root.addView(buildKeypad())

        root.setOnClickListener { hideKeyboard() }
        setContentView(root)
    }

    private fun buildHeader(): LinearLayout {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 15f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, dp(10), 0)
        }
        h.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        h.addView(headerButton("theme") { toggleTheme() })
        h.addView(headerButton("clear") { output.setText("") })
        h.addView(headerButton("kb") { toggleKeyboard() })
        return h
    }

    private fun headerButton(label: String, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = label
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { onClick() }
        }
        tinted.add(b)
        return b
    }

    private fun buildInputRow(): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
        }
        prompt = TextView(this).apply {
            text = "> "
            textSize = 16f
            typeface = Typeface.MONOSPACE
        }
        input = EditText(this).apply {
            textSize = 16f
            typeface = Typeface.MONOSPACE
            isSingleLine = true
            isFocusableInTouchMode = true
            background = null
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setRawInputType(
                android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            )
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    execute(input.text.toString())
                    true
                } else false
            }
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> { historyUp(); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { historyDown(); true }
                    KeyEvent.KEYCODE_DPAD_LEFT -> { moveCursor(-1); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { moveCursor(1); true }
                    else -> false
                }
            }
        }
        r.addView(prompt)
        r.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return r
    }

    private fun buildKeypad(): View {
        val keyPad = GridLayout(this).apply {
            columnCount = 6
            rowCount = 5
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        val INS = 0; val BSP = 1; val EVL = 2; val CLR = 3
        val LEFT = 4; val RIGHT = 5; val UP = 6; val DOWN = 7
        data class Key(val label: String, val type: Int, val insert: String? = null, val mono: Boolean = true)

        val keys = listOf(
            Key("7", INS, "7"), Key("8", INS, "8"), Key("9", INS, "9"),
            Key("(", INS, "("), Key(")", INS, ")"), Key("DEL", BSP),
            Key("4", INS, "4"), Key("5", INS, "5"), Key("6", INS, "6"),
            Key("/", INS, "/"), Key("*", INS, "*"), Key("^", INS, "^"),
            Key("1", INS, "1"), Key("2", INS, "2"), Key("3", INS, "3"),
            Key("+", INS, "+"), Key("-", INS, "-"), Key("%", INS, "%"),
            Key("0", INS, "0"), Key(".", INS, "."), Key("pi", INS, "pi"),
            Key("e", INS, "e"), Key("sqrt", INS, "sqrt("), Key("=", EVL),
            Key("\u2190", LEFT, mono = false), Key("\u2192", RIGHT, mono = false),
            Key("\u2191", UP, mono = false), Key("\u2193", DOWN, mono = false),
            Key("ans", INS, "ans"), Key("CLR", CLR)
        )
        keys.forEach { k ->
            keyPad.addView(keyButton(k.label, k.mono) {
                when (k.type) {
                    BSP -> backspace()
                    EVL -> execute(input.text.toString())
                    CLR -> clearEntry()
                    LEFT -> moveCursor(-1)
                    RIGHT -> moveCursor(1)
                    UP -> historyUp()
                    DOWN -> historyDown()
                    else -> insert(k.insert!!)
                }
            })
        }
        return keyPad
    }

    private fun keyButton(label: String, mono: Boolean = true, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = label
            textSize = 17f
            typeface = if (mono) Typeface.MONOSPACE else Typeface.DEFAULT
            setPadding(0, 0, 0, 0)
            setOnClickListener { onClick() }
            setOnTouchListener { v, ev ->
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                    val fb = ColorDrawable(fg())
                    v.background = fb
                    (v as Button).setTextColor(bg())
                    v.invalidate()
                } else if (ev.actionMasked == MotionEvent.ACTION_UP ||
                           ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                    styleKey(v)
                    v.invalidate()
                }
                false
            }
        }
        val lp = GridLayout.LayoutParams()
        lp.width = 0
        lp.height = dp(50)
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        lp.setMargins(dp(3), dp(3), dp(3), dp(3))
        b.layoutParams = lp
        tinted.add(b)
        return b
    }

    private fun styleKey(v: View) {
        v.background = ColorDrawable(bg())
        (v as Button).setTextColor(fg())
    }

    // ------------------------------------------------------------------ theme

    private fun bg(): Int = if (dark) Color.BLACK else Color.WHITE
    private fun fg(): Int = if (dark) Color.WHITE else Color.BLACK

    private fun applyTheme() {
        root.setBackgroundColor(bg())
        output.setTextColor(fg())
        prompt.setTextColor(fg())
        input.setTextColor(fg())
        input.setHintTextColor(fg())
        title.setTextColor(fg())
        tinted.forEach { styleKey(it) }
        window.statusBarColor = bg()
        window.navigationBarColor = bg()
        val decor = window.decorView
        val flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        decor.systemUiVisibility = if (dark) decor.systemUiVisibility and flags.inv() else decor.systemUiVisibility or flags
    }

    private fun toggleTheme() {
        dark = !dark
        applyTheme()
        writeLine(if (dark) "theme: OLED black" else "theme: light")
    }

    // ------------------------------------------------------------------ input actions

    private fun insert(s: String) {
        val text = input.editableText
        val start = input.selectionStart
        val pos = if (start >= 0) start else text.length
        text.insert(pos, s)
        input.setSelection(pos + s.length)
    }

    private fun backspace() {
        val text = input.editableText
        val s = input.selectionStart
        if (s > 0) text.delete(s - 1, s)
    }

    private fun clearEntry() {
        input.setText("")
    }

    private fun moveCursor(delta: Int) {
        val len = input.text.length
        val s = input.selectionStart
        val t = when {
            s < 0 -> 0
            delta < 0 -> s - 1
            else -> s + 1
        }
        input.setSelection(t.coerceIn(0, len))
    }

    private fun historyUp() {
        if (histIndex > 0) { histIndex--; restoreHistory() }
    }

    private fun historyDown() {
        if (histIndex < history.size) { histIndex++; restoreHistory() }
    }

    private fun execute(raw: String) {
        if (raw.isBlank()) return
        val s = raw.trim()
        writeLine("> $s")
        when (s.lowercase()) {
            "help" -> writeHelp()
            "clear", "cls" -> output.setText("")
            "theme" -> toggleTheme()
            "version" -> writeLine("CL Calc v1.0 - OLED basic, Kotlin")
            "about" -> writeLine("A basic command-line calculator inspired by clcalc.net")
            "exit", "quit" -> writeLine("just close the app to exit")
            else -> try {
                writeLine(Eval.evaluate(s))
            } catch (e: Exception) {
                writeLine("Error: ${e.message ?: "unknown"}")
            }
        }
        history.add(s)
        histIndex = history.size
        input.setText("")
    }

    private fun writeHelp() {
        writeLine("CL Calc basic edition")
        writeLine("")
        writeLine("Constants:  pi  e  phi  tau  ans (last result)")
        writeLine("Operators:  +  -  *  /  ^  !  %")
        writeLine("  %  = percentage  (50 + 3% = 51.5,  200 * 10% = 20)")
        writeLine("  !  = factorial        (e.g. 5! = 120)")
        writeLine("Percentages: (1+10%)*(2+20%)")
        writeLine("Implicit multiply: 2pi  = 2*pi,  2(3+4)")
        writeLine("")
        writeLine("Functions: (radians for trig)")
        writeLine("  sqrt  cbrt  abs  sign  round  floor  ceil  trunc  frac")
        writeLine("  exp  ln  log  log2  pow  hypot  atan2")
        writeLine("  min  max  mod  gcd  lcm  fact")
        writeLine("  sin  cos  tan  asin  acos  atan  sec  csc  cot")
        writeLine("  rand()  rand(n)  rand(lo, hi)")
        writeLine("")
        writeLine("Commands:  help  clear/cls  theme  version  about")
        writeLine("Examples:")
        writeLine("  (2 + 3) * 5        -> 25")
        writeLine("  2^10               -> 1024")
        writeLine("  sqrt(2)            -> 1.414213562373")
        writeLine("  12/5 + 3           -> 5.4")
    }

    private fun writeLine(s: String) {
        output.append(s)
        output.append("\n")
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun restoreHistory() {
        input.setText(if (histIndex >= history.size) "" else history[histIndex])
        input.setSelection(input.text.length)
    }

    // ------------------------------------------------------------------ misc

    private fun toggleKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        if (imm.isActive) imm.hideSoftInputFromWindow(input.windowToken, 0)
        else input.requestFocus()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}