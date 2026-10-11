package com.scarface.reverse

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import android.view.KeyEvent
import java.io.ByteArrayOutputStream
import java.util.Locale

/** Offline, read-only mobile binary inspector. No networking or process attachment. */
class MainActivity : Activity() {
    private val navy = Color.rgb(58, 12, 20)
    private val canvasColor = Color.rgb(13, 15, 19)
    private val ink = Color.rgb(218, 233, 225)
    private val border = Color.rgb(96, 39, 48)
    private val mono = Typeface.MONOSPACE
    private lateinit var root: LinearLayout
    private lateinit var workspace: LinearLayout
    private lateinit var command: EditText
    private lateinit var keypad: LinearLayout
    private lateinit var status: TextView
    private lateinit var cpu: TextView
    private lateinit var info: TextView
    private lateinit var hex: TextView
    private lateinit var stack: TextView
    private val panels = mutableMapOf<String, LinearLayout>()
    private var activeTab = "CPU"
    private var bytes = ByteArray(0)
    private var filename = "No file loaded"
    private var offset = 0
    private val maxRead = 128 * 1024 * 1024
    private val requestOpen = 40
    private var keypadVisible = false

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opt out of edge-to-edge enforcement on Android 15; keep title below system status bar.
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER }
        }
        window.decorView.systemUiVisibility = 0
        window.statusBarColor = navy
        window.navigationBarColor = navy
        buildUi()
        showEmpty()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(canvasColor)
        }
        val title = TextView(this).apply {
            text = "🐺 WOLFDBG  //  v0.6"
            textSize = 16f
            setTextColor(Color.rgb(255, 100, 108))
            setBackgroundColor(navy)
            setPadding(10.dp(), 7.dp(), 8.dp(), 7.dp())
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        root.addView(title)

        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun topButton(label: String, weight: Float = 1f, action: () -> Unit) {
            val b = Button(this).apply {
                text = label; textSize = 10f; isAllCaps = false
                setTextColor(Color.WHITE); setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(91, 24, 35)))
                minHeight = 0; minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setOnClickListener { action() }
            }
            toolbar.addView(b, LinearLayout.LayoutParams(0, 43.dp(), weight))
        }
        topButton("OPEN") { openFile() }
        topButton("CPU") { selectTab("CPU") }
        topButton("INFO") { selectTab("INFO") }
        topButton("STACK") { selectTab("STACK") }
        topButton("HEX") { selectTab("HEX") }
        topButton("STRINGS") { showStrings(); selectTab("CPU") }
        root.addView(toolbar)

        // Keep the input at the top on a phone, never hidden below tall panes.

        workspace = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cpu = makePane(top, "CPU", "01  /  BYTE VIEW", 2f)
        info = makePane(top, "INFO", "02  /  REGISTERS + INFO", 1f)
        hex = makePane(bottom, "HEX", "03  /  HEX + ASCII", 2f)
        stack = makePane(bottom, "STACK", "04  /  STACK + STRUCTURE", 1f)
        workspace.addView(top, LinearLayout.LayoutParams(-1, 0, 1f))
        workspace.addView(bottom, LinearLayout.LayoutParams(-1, 0, 1f))

        val cmdRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(navy)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4.dp(), 2.dp(), 4.dp(), 2.dp())
        }
        val prompt = TextView(this).apply { text = " wolf> "; textSize = 15f; setTextColor(Color.WHITE) }
        cmdRow.addView(prompt)
        command = EditText(this).apply {
            textSize = 13f; setSingleLine(true)
            hint = "Tap to type: help, goto, search"
            setHintTextColor(Color.rgb(178, 154, 158))
            setBackgroundColor(Color.rgb(34, 31, 39))
            setPadding(8.dp(), 0, 8.dp(), 0)
            setTextColor(Color.WHITE)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            isFocusable = true
            isFocusableInTouchMode = true
            showSoftInputOnFocus = true
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && !keypadVisible) {
                    post { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT) }
                }
            }
            setOnEditorActionListener { _, _, event ->
                if (event == null || event.action == KeyEvent.ACTION_DOWN) runCommand()
                true
            }
        }
        cmdRow.addView(command, LinearLayout.LayoutParams(0, 43.dp(), 1f))
        command.setOnClickListener {
            command.requestFocus()
            if (!keypadVisible) (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(command, InputMethodManager.SHOW_IMPLICIT)
        }
        val run = Button(this).apply {
            text = "RUN"; textSize = 11f; isAllCaps = false
            setOnClickListener { runCommand() }
        }
        cmdRow.addView(run, LinearLayout.LayoutParams(48.dp(), 43.dp()))
        val toggle = Button(this).apply {
            text = "0-F"; textSize = 10f; isAllCaps = false
            setOnClickListener {
                keypadVisible = !keypadVisible
                keypad.visibility = if (keypadVisible) View.VISIBLE else View.GONE
                if (keypadVisible) {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(command.windowToken, 0)
                } else {
                    command.requestFocus()
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(command, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
        cmdRow.addView(toggle, LinearLayout.LayoutParams(66.dp(), 43.dp()))
        root.addView(cmdRow)

        keypad = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(31, 25, 30))
            visibility = View.GONE
        }
        listOf("789A", "456B", "123C", "0DEF").forEach { keys ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            keys.forEach { key ->
                val b = Button(this).apply {
                    text = key.toString(); textSize = 14f; isAllCaps = false
                    setOnClickListener {
                        insertAtCursor(key.toString())
                    }
                }
                row.addView(b, LinearLayout.LayoutParams(0, 43.dp(), 1f))
            }
            keypad.addView(row)
        }
        val keyActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun keyAction(label: String, action: () -> Unit) {
            val b = Button(this).apply { text = label; textSize = 11f; isAllCaps = false; setOnClickListener { action() } }
            keyActions.addView(b, LinearLayout.LayoutParams(0, 42.dp(), 1f))
        }
        keyAction("SPACE") { insertAtCursor(" ") }
        keyAction("⌫") {
            val pos = command.selectionStart.coerceAtLeast(0)
            if (pos > 0) command.text.delete(pos - 1, pos)
        }
        keyAction("CLEAR") { command.setText("") }
        keyAction("ENTER ↵") { runCommand() }
        keypad.addView(keyActions)
        root.addView(keypad)
        root.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this).apply {
            textSize = 10f; setTextColor(Color.rgb(255, 190, 192)); setBackgroundColor(navy)
            setPadding(8.dp(), 5.dp(), 8.dp(), 5.dp())
            text = "WOLFDBG | READY | OFFLINE | READ-ONLY"
        }
        root.addView(status)
        setContentView(root)
        selectTab("CPU")
        // Avoid stealing focus from the editable command bar on initial launch.
        command.clearFocus()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun insertAtCursor(value: String) {
        val position = command.selectionStart.coerceIn(0, command.text.length)
        command.text.insert(position, value)
        command.requestFocus()
        command.setSelection((position + value.length).coerceAtMost(command.text.length))
    }

    private fun makePane(parent: LinearLayout, key: String, heading: String, weight: Float): TextView {
        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(border)
            setPadding(1.dp(), 1.dp(), 1.dp(), 1.dp())
        }
        val label = TextView(this).apply {
            text = heading; textSize = 10f; setTextColor(Color.rgb(255, 184, 185))
            setBackgroundColor(navy); setPadding(5.dp(), 3.dp(), 3.dp(), 3.dp())
        }
        frame.addView(label)
        val content = TextView(this).apply {
            typeface = mono; textSize = 10f; setTextColor(ink)
            setBackgroundColor(Color.rgb(19, 23, 28))
            setPadding(5.dp(), 5.dp(), 5.dp(), 5.dp())
            setTextIsSelectable(true)
            // Never wrap hex rows: use horizontal scrolling.
            setHorizontallyScrolling(true)
        }
        val horizontal = HorizontalScrollView(this).apply {
            isFillViewport = false
            addView(content)
        }
        val vertical = ScrollView(this).apply { addView(horizontal) }
        frame.addView(vertical, LinearLayout.LayoutParams(-1, 0, 1f))
        parent.addView(frame, LinearLayout.LayoutParams(0, -1, weight))
        panels[key] = frame
        return content
    }

    private fun selectTab(tab: String) {
        activeTab = tab
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape) {
            panels.values.forEach { it.visibility = View.VISIBLE }
            // Restore the 2x2 workspace.
            for (i in 0 until workspace.childCount) {
                val row = workspace.getChildAt(i) as LinearLayout
                row.visibility = View.VISIBLE
                row.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            }
        } else {
            // Keep classic panels in landscape; use one readable pane on narrow screens.
            val shown = if (tab == "CPU") "CPU" else if (tab == "HEX") "HEX" else if (tab == "INFO") "INFO" else "STACK"
            panels.forEach { (name, panel) -> panel.visibility = if (name == shown) View.VISIBLE else View.GONE }
            for (i in 0 until workspace.childCount) {
                val row = workspace.getChildAt(i) as LinearLayout
                row.visibility = if (row.childrenVisible()) View.VISIBLE else View.GONE
                row.layoutParams = LinearLayout.LayoutParams(-1, 0, if (row.visibility == View.VISIBLE) 1f else 0f)
            }
        }
    }

    private fun LinearLayout.childrenVisible(): Boolean = (0 until childCount).any { getChildAt(it).visibility == View.VISIBLE }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        selectTab(activeTab)
    }

    private fun showEmpty() {
        cpu.text = "WOLFDBG // STATIC INSPECTOR\n\nOPEN   Load a binary or APK\nHELP   List commands\nHEX    Inspect bytes\nSTRINGS  Find printable text\n\nUse landscape for the 4-pane workspace.\n\nUse format for ELF / PE / DEX / APK. Use arm64 for a limited instruction preview."
        info.text = "EAX  --------  EBX  --------\nECX  --------  EDX  --------\nESP  --------  EBP  --------\nEIP  --------  EFLAGS  ----\n\nNO LIVE PROCESS ATTACHED"
        hex.text = "Waiting for file..."
        stack.text = "STACK: NOT AVAILABLE\n\nOffline inspection mode"
    }

    private fun openFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }, requestOpen)
    }

    @Deprecated("Activity result API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode != requestOpen || resultCode != RESULT_OK) return
        val uri = resultData?.data ?: return
        try {
            val loaded = readLimited(uri)
            bytes = loaded
            filename = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
            offset = 0
            showCpu()
            selectTab("CPU")
            status.text = "OPEN | ${bytes.size} BYTES | READ-ONLY"
        } catch (e: Exception) {
            status.text = "ERROR | ${e.message ?: "Could not read file"}"
            Toast.makeText(this, "Open failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun readLimited(uri: Uri): ByteArray {
        val out = ByteArrayOutputStream()
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "No file stream" }
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(out.size() + count <= maxRead) { "File exceeds 128 MiB inspection limit" }
                out.write(buffer, 0, count)
            }
        }
        return out.toByteArray()
    }

    private fun format(): String = when {
        bytes.size >= 4 && bytes[0] == 0x7F.toByte() && bytes[1] == 69.toByte() && bytes[2] == 76.toByte() && bytes[3] == 70.toByte() -> "ELF"
        bytes.size >= 2 && bytes[0] == 77.toByte() && bytes[1] == 90.toByte() -> "MZ / PE candidate"
        bytes.size >= 4 && bytes[0] == 80.toByte() && bytes[1] == 75.toByte() -> "ZIP / APK candidate"
        bytes.size >= 4 && bytes[0] == 100.toByte() && bytes[1] == 101.toByte() && bytes[2] == 120.toByte() && bytes[3] == 10.toByte() -> "DEX"
        else -> "Unknown / raw binary"
    }

    private fun dump(start: Int, rowCount: Int = 128): String = buildString {
        append("OFFSET    HEX BYTES                        ASCII\n")
        if (bytes.isEmpty()) return@buildString
        val begin = (start.coerceIn(0, bytes.lastIndex) / 8) * 8
        val end = minOf(bytes.size.toLong(), begin.toLong() + rowCount.toLong() * 8L).toInt()
        for (at in begin until end step 8) {
            append(String.format(Locale.US, "%08X  ", at))
            for (i in 0 until 8) {
                if (at + i < bytes.size) append(String.format(Locale.US, "%02X ", bytes[at + i].toInt() and 255))
                else append("   ")
            }
            append(" ")
            for (i in at until minOf(at + 8, bytes.size)) {
                val v = bytes[i].toInt() and 255
                append(if (v in 32..126) v.toChar() else '.')
            }
            append('\n')
        }
        if (end < bytes.size) append("\nUse goto <hex-address> to inspect more bytes.")
    }

    private fun showCpu() {
        if (bytes.isEmpty()) { showEmpty(); return }
        cpu.text = "BYTE VIEW  //  NO CPU DECODER\n\n" + dump(offset, 48)
        hex.text = dump(offset)
        info.text = "EAX  --------  EBX  --------\nECX  --------  EDX  --------\nESP  --------  EBP  --------\nEIP  --------  EFLAGS  ----\n\nFILE: $filename\nTYPE: ${format()}\nSIZE: ${bytes.size} bytes\nOFFSET: 0x${offset.toString(16).uppercase(Locale.US)}\n\nREGISTERS UNAVAILABLE\nNO PROCESS ATTACHED"
        stack.text = "STACK: NOT AVAILABLE\nNO PROCESS ATTACHED\n\nFILE SIGNATURE\n${signaturePreview()}\n\nOnly analyse files you own or are authorised to inspect."
    }

    private fun signaturePreview(): String {
        if (bytes.isEmpty()) return "No file loaded"
        return bytes.take(16).joinToString(" ") { "%02X".format(Locale.US, it.toInt() and 255) }
    }

    private fun showStrings() {
        if (bytes.isEmpty()) { status.text = "Open a file first"; return }
        val results = mutableListOf<String>()
        var begin = -1
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 255
            if (v in 32..126) {
                if (begin < 0) begin = i
            } else if (begin >= 0) {
                if (i - begin >= 4 && results.size < 400) {
                    results.add(String.format(Locale.US, "%08X  %s", begin, String(bytes, begin, minOf(i - begin, 160), Charsets.US_ASCII)))
                }
                begin = -1
            }
        }
        if (begin >= 0 && bytes.size - begin >= 4 && results.size < 400) {
            results.add(String.format(Locale.US, "%08X  %s", begin, String(bytes, begin, minOf(bytes.size - begin, 160), Charsets.US_ASCII)))
        }
        cpu.text = "ASCII STRINGS (${results.size} shown)\n\n" + results.joinToString("\n")
        status.text = "STRINGS | ${results.size} FOUND | READ-ONLY"
    }

    private fun parseHexAddress(raw: String): Int {
        val cleaned = raw.trim().removePrefix("0x").removePrefix("0X")
        require(cleaned.isNotEmpty() && cleaned.all { it in "0123456789abcdefABCDEF" }) { "Enter a hexadecimal address" }
        return cleaned.toLong(16).also { require(it <= Int.MAX_VALUE) { "Address too large" } }.toInt()
    }

    private fun runCommand() {
        val raw = command.text.toString().trim()
        if (raw.isEmpty()) return
        val parts = raw.split(Regex("\\s+"), limit = 2)
        val op = parts[0].lowercase(Locale.US)
        val arg = parts.getOrNull(1).orEmpty()
        try {
            when (op) {
                "help" -> {
                    info.text = "WOLFDBG COMMANDS\n\nhelp         Show this page\ninfo         File + registers status\nhex          Hex/ASCII dump\nstack        Stack/structure panel\nstrings      Printable ASCII strings\ngoto 0x40    Jump to file offset\nsearch 4D 5A Find a hex pattern\nformat       ELF / PE / DEX / APK headers\narm64        AArch64 instruction preview\ndisasm       Alias for arm64 (limited)\nclear        Clear input\n\nRead-only file inspection. No live debugging."
                    selectTab("INFO")
                    status.text = "HELP | COMMANDS LISTED"
                }
                "info" -> { showCpu(); selectTab("INFO"); status.text = "FILE INFO" }
                "hex" -> { showCpu(); selectTab("HEX"); status.text = "HEX | OFFSET 0x${offset.toString(16)}" }
                "strings" -> { showStrings(); selectTab("CPU") }
                "format", "headers", "elf", "pe", "dex", "apk" -> {
                    require(bytes.isNotEmpty()) { "Open a file first" }
                    info.text = BinaryResearch.report(bytes)
                    selectTab("INFO")
                    status.text = "FORMAT | STATIC INSPECTION"
                }
                "arm64", "disasm" -> {
                    require(bytes.isNotEmpty()) { "Open a file first" }
                    cpu.text = BinaryResearch.arm64(bytes, offset)
                    selectTab("CPU")
                    status.text = "ARM64 | PARTIAL DECODER | READ-ONLY"
                }
                "stack" -> { showCpu(); selectTab("STACK"); status.text = "STACK | STATIC MODE" }
                "goto" -> {
                    require(bytes.isNotEmpty()) { "Open a file first" }
                    val target = parseHexAddress(arg)
                    require(target in bytes.indices) { "Address outside file" }
                    offset = target
                    showCpu(); selectTab("HEX")
                    status.text = "GOTO | 0x${offset.toString(16).uppercase(Locale.US)}"
                }
                "search" -> {
                    require(bytes.isNotEmpty()) { "Open a file first" }
                    val digits = arg.replace(Regex("\\s+"), "").removePrefix("0x").removePrefix("0X")
                    require(digits.isNotEmpty() && digits.length % 2 == 0 && digits.length <= 256 && digits.all { it in "0123456789abcdefABCDEF" }) { "Use even-length hex bytes, e.g. search 4D 5A" }
                    val needle = ByteArray(digits.length / 2) { i -> digits.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
                    var found = -1
                    for (i in 0 until (bytes.size - needle.size + 1).coerceAtLeast(0)) {
                        if (needle.indices.all { j -> bytes[i + j] == needle[j] }) { found = i; break }
                    }
                    if (found >= 0) {
                        offset = found; showCpu(); selectTab("HEX")
                        status.text = "FOUND | 0x${found.toString(16).uppercase(Locale.US)}"
                    } else status.text = "NOT FOUND | $arg"
                }
                "clear" -> { command.setText(""); status.text = "READY" }
                else -> status.text = "UNKNOWN COMMAND | type help"
            }
            if (op != "clear") command.setText("")
        } catch (e: IllegalArgumentException) {
            status.text = "ERROR | ${e.message}"
        } catch (e: Exception) {
            status.text = "ERROR | ${e.message ?: "Command failed"}"
        }
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(command.windowToken, 0)
        command.clearFocus()
    }
}
