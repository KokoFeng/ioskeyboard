package com.feng.ioskeyboard

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.ClipDescription
import android.net.Uri
import android.widget.Toast
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File

class IosKeyboardService : InputMethodService(), KeyboardView.Listener {

    private lateinit var kv: KeyboardView
    @Volatile private var pred = Predictor(emptyList())
    private val word = StringBuilder()
    private var prevWord: String? = null
    private var suggestOn = true
    private var lastSpaceAt = 0L
    private var pendingMic: String? = null
    private var started = false          // saran kata baru muncul setelah pengguna mulai mengetik
    private var autoCorrect = true
    private var sr: SpeechRecognizer? = null
    private var listening = false

    private val learnFile get() = File(filesDir, "learn.txt")

    override fun onCreate() {
        super.onCreate()
        loadPredictor()
    }

    private fun loadPredictor() {
        val lang = getSharedPreferences("kb", Context.MODE_PRIVATE).getInt("lang", 0)
        Thread {
            val id = readWords("words_id.txt"); val en = readWords("words_en.txt")
            // bahasa terpilih diutamakan, bahasa lain tetap dikenali (tidak dianggap salah ketik)
            val p = Predictor(if (lang == 0) id + en else en + id)
            p.load(learnFile)
            pred = p
        }.start()
    }

    private fun readWords(f: String): List<String> = try {
        assets.open(f).bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
    } catch (_: Exception) { emptyList() }

    override fun onFinishInput() {
        super.onFinishInput()
        pred.save(learnFile)
    }

    override fun onOpenSettings() {
        startActivity(Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onLanguage(i: Int) {
        pred.save(learnFile)
        loadPredictor()
        word.setLength(0)
        refresh()
    }

    override fun onDestroy() {
        pred.save(learnFile)
        try { sr?.destroy() } catch (_: Exception) {}
        if (::kv.isInitialized) kv.release()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        kv = KeyboardView(this, this)
        window?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        applyPrefs()
        return kv
    }

    override fun onEvaluateFullscreenMode() = false

    @Suppress("DEPRECATION")
    private fun applyPrefs() {
        val pf = getSharedPreferences("kb", Context.MODE_PRIVATE)
        autoCorrect = pf.getBoolean("autocorrect", true)
        if (::kv.isInitialized) kv.loadPrefs()
        try {
            val w = window?.window ?: return
            val follow = pf.getBoolean("nav_follow", true)
            val isLight = ::kv.isInitialized && kv.isLight()
            val col = if (follow && ::kv.isInitialized) kv.bgColor() else (if (isLight) Color.WHITE else Color.BLACK)
            w.navigationBarColor = col
            if (Build.VERSION.SDK_INT >= 28) w.navigationBarDividerColor = col
            if (Build.VERSION.SDK_INT >= 26) {
                val f = w.decorView.systemUiVisibility
                w.decorView.systemUiVisibility = if (isLight) f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    else f and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
        } catch (_: Exception) {}
    }

    override fun onStartInputView(ei: EditorInfo, restarting: Boolean) {
        super.onStartInputView(ei, restarting)
        val cls = ei.inputType and InputType.TYPE_MASK_CLASS
        val variation = ei.inputType and InputType.TYPE_MASK_VARIATION
        suggestOn = cls == InputType.TYPE_CLASS_TEXT &&
            variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
            variation != InputType.TYPE_TEXT_VARIATION_URI &&
            (ei.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0
        val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
        val multi = (ei.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        val hasAction = action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
        kv.showReturnArrow = hasAction || !multi
        kv.returnLabel = "return"
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) kv.setNumberPage() else kv.resetPage()
        applyPrefs()
        started = false
        kv.reloadStickers()
        word.setLength(0); prevWord = null
        resync()
        kv.shift = 0
        updateShiftFromContext()
        refresh()
        pendingMic?.let { currentInputConnection?.commitText(it, 1); pendingMic = null }
    }

    override fun onUpdateSelection(os: Int, oe: Int, ns: Int, ne: Int, cs: Int, ce: Int) {
        super.onUpdateSelection(os, oe, ns, ne, cs, ce)
        // kursor dipindah dari luar (bukan oleh keyboard) -> baca ulang kata saat ini
        if (SystemClock.uptimeMillis() - lastOwnEdit > 500 && ::kv.isInitialized) { resync(); refresh() }
    }

    private var lastOwnEdit = 0L

    // ---------- aksi tombol ----------
    override fun onText(s: String) {
        val ic = currentInputConnection ?: return
        lastOwnEdit = SystemClock.uptimeMillis()
        started = true
        ic.commitText(s, 1)
        val c = s[0]
        if (s.length == 1 && (c.isLetter() || (c == '\'' && word.isNotEmpty()))) word.append(c)
        else { finishWord(); if (s == "." || s == "!" || s == "?") updateShiftFromContext() }
        lastSpaceAt = 0
        refresh()
    }

    override fun onDelete() {
        val ic = currentInputConnection ?: return
        lastOwnEdit = SystemClock.uptimeMillis()
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) { ic.commitText("", 1); word.setLength(0); refresh(); return }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        if (word.isNotEmpty()) word.deleteCharAt(word.length - 1)
        if (word.isEmpty()) resync()
        lastSpaceAt = 0
        refresh()
    }

    override fun onSpace() {
        val ic = currentInputConnection ?: return
        lastOwnEdit = SystemClock.uptimeMillis()
        started = true
        val now = SystemClock.uptimeMillis()
        // ketuk spasi 2x -> titik + spasi
        if (lastSpaceAt != 0L && now - lastSpaceAt < 600 && word.isEmpty()) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0); ic.commitText(". ", 1)
                lastSpaceAt = 0; updateShiftFromContext(); refresh(); return
            }
        }
        if (suggestOn && autoCorrect && word.isNotEmpty()) {
            val fix = pred.autocorrect(word.toString())
            if (fix != null) {
                ic.deleteSurroundingText(word.length, 0)
                val out = matchCase(word.toString(), fix)
                ic.commitText(out, 1)
                word.setLength(0); word.append(out)
            }
        }
        ic.commitText(" ", 1)
        finishWord()
        lastSpaceAt = now
        updateShiftFromContext()
        refresh()
    }

    override fun onReturn() {
        val ic = currentInputConnection ?: return
        lastOwnEdit = SystemClock.uptimeMillis()
        finishWord()
        val ei = currentInputEditorInfo
        val action = ei?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        if (ei == null || (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        } else ic.performEditorAction(action)
        updateShiftFromContext()
        refresh()
    }

    override fun onShiftChange(state: Int) {}

    override fun onCreateSticker() {
        startActivity(Intent(this, StickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onSticker(f: File) {
        val ei = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val ok = EditorInfoCompat.getContentMimeTypes(ei).any { ClipDescription.compareMimeTypes("image/png", it) }
        if (!ok) { Toast.makeText(this, "Aplikasi ini tidak mendukung stiker dari keyboard", Toast.LENGTH_SHORT).show(); return }
        val uri: Uri = StickerStore.uri(this, f)
        var flags = 0
        if (Build.VERSION.SDK_INT >= 25) flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        else grantUriPermission(ei.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val info = InputContentInfoCompat(uri, ClipDescription("stiker", arrayOf("image/png")), null)
        InputConnectionCompat.commitContent(ic, ei, info, flags, null)
    }

    override fun onCursorMove(dir: Int) {
        sendDownUpKeyEvents(if (dir > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
        word.setLength(0)
    }

    override fun onGlobe(long: Boolean) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (long) imm.showInputMethodPicker()
        else if (!switchToNextInputMethod(false)) imm.showInputMethodPicker()
    }

    override fun onMic() {
        if (listening) { sr?.stopListening(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // izin mikrofon belum ada: minta lewat activity kecil yang tidak membuka aplikasi utama
            startActivity(Intent(this, MicActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Pengenalan suara tidak tersedia di HP ini", Toast.LENGTH_SHORT).show(); return
        }
        try { sr?.destroy() } catch (_: Exception) {}
        sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) {
                setListening(false)
                val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrEmpty()) {
                    started = true
                    currentInputConnection?.commitText("$t ", 1)
                    word.setLength(0); prevWord = t.trim().split(' ').lastOrNull()?.lowercase()
                    refresh()
                }
            }
            override fun onError(e: Int) { setListening(false) }
            override fun onReadyForSpeech(p: Bundle?) { setListening(true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        sr?.startListening(i)
        setListening(true)
    }

    private fun setListening(v: Boolean) {
        listening = v
        if (::kv.isInitialized) kv.micActive = v
    }

    // ---------- saran kata ----------
    private var sugList = arrayOf("", "", "")

    override fun onSuggestion(slot: Int) {
        val ic = currentInputConnection ?: return
        val t = sugList[slot]
        if (t.isEmpty()) return
        lastOwnEdit = SystemClock.uptimeMillis()
        val typed = word.toString()
        if (slot == 0 && typed.isNotEmpty()) {
            // kata ketikan sendiri: simpan apa adanya
            pred.learn(prevWord, typed)
            ic.commitText(" ", 1)
        } else {
            val out = if (typed.isNotEmpty()) matchCase(typed, t) else t
            if (typed.isNotEmpty()) ic.deleteSurroundingText(typed.length, 0)
            ic.commitText("$out ", 1)
            word.setLength(0); word.append(out)
        }
        finishWord()
        lastSpaceAt = 0
        updateShiftFromContext()
        refresh()
    }

    private fun refresh() {
        if (!::kv.isInitialized) return
        if (!suggestOn) { kv.probs = emptyMap(); kv.setSuggestions("", "", "", false); sugList = arrayOf("", "", ""); return }
        val w = word.toString()
        if (w.isEmpty() && !started) {
            sugList = arrayOf("", "", ""); kv.probs = emptyMap(); kv.setSuggestions("", "", "", false)
        } else if (w.isEmpty()) {
            val n = pred.nextWord(prevWord)
            sugList = arrayOf(n.getOrElse(0) { "" }, n.getOrElse(1) { "" }, n.getOrElse(2) { "" })
            kv.probs = emptyMap()
            kv.setSuggestions(sugList[0], sugList[1], sugList[2], false)
        } else {
            val fix = if (autoCorrect) pred.autocorrect(w) else null
            val s = pred.suggest(w, prevWord, 3)
            val best = fix ?: s.getOrNull(0) ?: ""
            val second = s.firstOrNull { it != best } ?: ""
            sugList = arrayOf(w, best, second)
            kv.probs = pred.nextLetters(w)
            kv.setSuggestions("«$w»", cased(w, best), cased(w, second), fix != null)
            // tampilan memakai teks dengan tanda « », daftar sugList menyimpan teks asli
        }
    }

    private fun cased(typed: String, s: String): String =
        if (s.isNotEmpty() && typed.isNotEmpty() && typed[0].isUpperCase()) s.replaceFirstChar { it.uppercase() } else s

    private fun matchCase(typed: String, s: String): String = when {
        typed.length > 1 && typed.all { it.isUpperCase() } -> s.uppercase()
        typed.isNotEmpty() && typed[0].isUpperCase() -> s.replaceFirstChar { it.uppercase() }
        else -> s
    }

    private fun finishWord() {
        if (word.isNotEmpty()) {
            val w = word.toString()
            if (suggestOn) pred.learn(prevWord, w)
            prevWord = w.lowercase()
            word.setLength(0)
        }
    }

    private fun resync() {
        val ic = currentInputConnection ?: return
        val t = ic.getTextBeforeCursor(40, 0)?.toString() ?: return
        word.setLength(0)
        var i = t.length
        while (i > 0 && (t[i - 1].isLetter() || t[i - 1] == '\'')) i--
        word.append(t.substring(i))
        var j = i
        while (j > 0 && !t[j - 1].isLetter()) j--
        var k = j
        while (k > 0 && t[k - 1].isLetter()) k--
        prevWord = if (j > k) t.substring(k, j).lowercase() else null
    }

    private fun updateShiftFromContext() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        if (kv.shift == 2) return
        val caps = ic.getCursorCapsMode(ei.inputType)
        kv.shift = if (caps != 0) 1 else 0
    }
}
