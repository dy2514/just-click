package com.hmgchallenge.seatpreset

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var vehicle: Vehicle
    private val seats = listOf("운전석" to SeatValue.ROW_1_LEFT, "동승석" to SeatValue.ROW_1_RIGHT, "뒷좌석 왼쪽" to SeatValue.ROW_2_LEFT, "뒷좌석 가운데" to SeatValue.ROW_2_CENTER, "뒷좌석 오른쪽" to SeatValue.ROW_2_RIGHT)
    private var openedSeat: SeatValue? = null
    private var seatMap: View? = null
    private var seatDetailPanel: View? = null
    private var seatDetailStatus: TextView? = null
    private val positions = mutableMapOf<SeatValue, MutableMap<String, String>>()
    private var scroll: ScrollView? = null
    private var statusView: TextView? = null
    private var wide = false
    private var saving = false
    private var status = "차량 연결 중"
    private var page = 0
    private var destroyed = false
    private var generation = 0
    private val prefs by lazy { getSharedPreferences("seat_presets", MODE_PRIVATE) }
    private val bg get() = getColor(R.color.seat_background)
    private val panel get() = getColor(R.color.seat_surface)
    private val ink get() = getColor(R.color.seat_text)
    private val accent get() = getColor(R.color.seat_accent)
    private val muted get() = getColor(R.color.seat_secondary)
    private val line get() = getColor(R.color.seat_line)
    private val regular by lazy { Typeface.create(resources.getFont(R.font.asta_sans), 400, false) }
    private val strong by lazy { Typeface.create(resources.getFont(R.font.asta_sans), 600, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vehicle = Vehicle(applicationContext)
        try { vehicle.initialize(); status = "차량 상태 확인 중" } catch (_: Exception) { status = "차량 서비스 연결 실패" }
        page = savedInstanceState?.getInt("page") ?: 0
        openedSeat = seats.firstOrNull { it.second.name == savedInstanceState?.getString("openedSeat") }?.second
        wide = resources.configuration.screenWidthDp >= 900
        refreshSeats()
        findViewById<View>(android.R.id.content).addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val width = right - left
            if (width <= 0) return@addOnLayoutChangeListener
            val next = width >= dp(900)
            if (next != wide) {
                wide = next
                ui { render() }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", page)
        outState.putString("openedSeat", openedSeat?.name)
    }

    override fun onDestroy() {
        destroyed = true
        generation++
        main.removeCallbacksAndMessages(null)
        vehicle.release()
        super.onDestroy()
    }

    private fun ui(block: () -> Unit) { main.post { if (!destroyed) block() } }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 16, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }
    private fun touchSurface(color: Int, radius: Int, stroke: Int? = null) =
        RippleDrawable(ColorStateList.valueOf(getColor(R.color.seat_ripple)), shape(color, radius, stroke), shape(Color.WHITE, radius))

    private fun text(s: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        typeface = if (bold) strong else regular
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun button(
        label: String,
        primary: Boolean = false,
        checked: Boolean = false,
        enabled: Boolean = true,
        action: () -> Unit
    ) = Button(this).apply {
        text = label
        textSize = 22f
        isAllCaps = false
        gravity = Gravity.CENTER
        typeface = strong
        minHeight = dp(64)
        minimumHeight = dp(64)
        minimumWidth = dp(64)
        stateListAnimator = null
        backgroundTintList = null
        val fill = when {
            !enabled -> getColor(R.color.seat_button)
            primary -> accent
            checked -> getColor(R.color.seat_selected)
            else -> getColor(R.color.seat_button)
        }
        setTextColor(when {
            !enabled -> muted
            primary -> getColor(R.color.seat_on_accent)
            checked -> accent
            else -> ink
        })
        background = touchSurface(fill, 16)
        setPadding(dp(20), dp(12), dp(20), dp(12))
        isEnabled = enabled
        isSelected = checked
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            action()
        }
    }
    private fun card() = column().apply {
        background = shape(panel, 20, line)
        setPadding(dp(24), dp(22), dp(24), dp(22))
    }
    private fun weighted() = LinearLayout.LayoutParams(0, -2, 1f)
    private fun gap() = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(12), 1) }
    private fun divider() = View(this).apply {
        setBackgroundColor(line)
        layoutParams = LinearLayout.LayoutParams(-1, dp(1))
    }
    private fun navigate(destination: Int) {
        if (destination == page) return
        page = destination
        openedSeat = null
        render(keepScroll = false)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (openedSeat != null) selectSeat(null) else super.onBackPressed()
    }

    private fun panelOnLeft(area: SeatValue?): Boolean =
        area == SeatValue.ROW_1_LEFT || area == SeatValue.ROW_2_LEFT

    private fun selectSeat(area: SeatValue?) {
        if (openedSeat == area) return
        val previousX = seatMap?.screenCenterX()
        val opensOnLeft = panelOnLeft(area)
        val opening = area != null && (openedSeat == null || panelOnLeft(openedSeat) != opensOnLeft)
        openedSeat = area
        render()
        seatMap?.let { map ->
            val listener = object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    map.viewTreeObserver.removeOnPreDrawListener(this)
                    if (previousX != null) {
                        map.translationX = previousX - map.screenCenterX()
                        map.animate().translationX(0f).setDuration(220).start()
                    }
                    if (opening) {
                        seatDetailPanel?.apply {
                            alpha = 0f
                            translationX = dp(if (opensOnLeft) -24 else 24).toFloat()
                            animate().alpha(1f).translationX(0f).setDuration(220).start()
                        }
                    }
                    return true
                }
            }
            map.viewTreeObserver.addOnPreDrawListener(listener)
        }
    }

    private fun View.screenCenterX(): Float {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return location[0] + width / 2f
    }
    private fun render(keepScroll: Boolean = true) {
        val previousY = if (keepScroll) scroll?.scrollY ?: 0 else 0
        val padding = dp(if (wide) 40 else 20)
        val frame = column().apply {
            setBackgroundColor(bg)
            setPadding(padding, dp(if (wide) 32 else 20), padding, 0)
        }
        seatMap = null
        seatDetailPanel = null
        seatDetailStatus = null
        val header = row()
        header.addView(text(getString(R.string.app_name), if (wide) 42f else 32f, bold = true).apply {
            isAccessibilityHeading = true
        }, weighted())
        frame.addView(header)
        frame.addView(space(20))
        val tabs = row().apply {
            background = shape(getColor(R.color.seat_button), 20)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            addView(button("좌석 상태", checked = page == 0) { navigate(0) }, weighted())
            addView(gap())
            addView(button("저장한 프리셋", checked = page == 1) { navigate(1) }, weighted())
        }
        frame.addView(tabs, LinearLayout.LayoutParams(if (wide) dp(464) else -1, -2))
        frame.addView(space(16))
        statusView = text(status, 20f, muted).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        frame.addView(statusView)
        frame.addView(space(20))
        val content = column().apply {
            if (page == 0) gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        if (page == 0) renderHome(content) else renderPresets(content)
        val viewport = ScrollView(this).apply { isFillViewport = page == 0; addView(content) }
        scroll = viewport
        frame.addView(viewport, LinearLayout.LayoutParams(-1, 0, 1f))
        if (page == 0) {
            frame.addView(divider())
            val actions = row().apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, dp(16))
                addView(button("새로고침", enabled = !saving) { refreshSeats() }, if (wide) LinearLayout.LayoutParams(-2, -2) else weighted())
            }
            frame.addView(actions)
            viewport.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
                // Balance the header and footer space so the map sits at the app window's center.
                val offset = top - (frame.height - bottom)
                val room = (viewport.height - (content.getChildAt(0)?.height ?: 0) - dp(24)).coerceAtLeast(0)
                val topPadding = dp(12) + (-offset).coerceIn(0, room)
                val bottomPadding = dp(12) + offset.coerceIn(0, room)
                if (content.paddingTop != topPadding || content.paddingBottom != bottomPadding) {
                    content.setPadding(0, topPadding, 0, bottomPadding)
                }
            }
        }
        setContentView(frame)
        if (previousY > 0) viewport.post { viewport.scrollTo(0, previousY) }
    }
    private fun renderHome(out: LinearLayout) {
        val container = card().apply { gravity = Gravity.CENTER_HORIZONTAL }
        seatMap = container
        container.addView(text("좌석을 선택하세요", 28f, bold = true))
        container.addView(space(6))
        container.addView(text("좌석을 누르면 현재 상태와 프리셋을 볼 수 있습니다.", 20f, muted))
        container.addView(space(24))
        fun seatRow(items: List<Pair<String, SeatValue>>) {
            val tiles = row().apply { gravity = Gravity.CENTER or Gravity.TOP }
            items.forEachIndexed { index, (name, area) ->
                if (index > 0) tiles.addView(gap())
                val seatButton = button(name, checked = openedSeat == area, enabled = !saving) {
                    selectSeat(if (openedSeat == area) null else area)
                }.apply {
                    if (!wide && openedSeat != null) {
                        textSize = 18f
                        setPadding(dp(4), dp(8), dp(4), dp(8))
                    }
                }
                tiles.addView(seatButton, LinearLayout.LayoutParams(0, dp(104), 1f))
            }
            container.addView(tiles, LinearLayout.LayoutParams(-1, -2))
        }
        seatRow(seats.take(2))
        container.addView(space(20))
        seatRow(seats.drop(2))
        container.addView(space(12))
        val area = openedSeat
        if (area == null) {
            out.addView(container, LinearLayout.LayoutParams(if (wide) dp(800) else -1, -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
        } else {
            val split = row().apply { gravity = Gravity.CENTER_VERTICAL }
            val detail = seatPanel(seats.first { it.second == area }.first, area)
            val mapLayout = LinearLayout.LayoutParams(0, -2, 3f)
            val detailLayout = LinearLayout.LayoutParams(0, -2, 2f)
            val left = panelOnLeft(area)
            if (left) split.addView(detail, detailLayout) else split.addView(container, mapLayout)
            split.addView(gap())
            if (left) split.addView(container, mapLayout) else split.addView(detail, detailLayout)
            out.addView(split)
        }
        updateSeatViews()
    }
    private fun seatPanel(name: String, area: SeatValue): LinearLayout {
        val matching = mutableListOf<JSONObject>()
        val all = presets()
        for (i in 0 until all.length()) {
            val item = all.getJSONObject(i); val saved = item.optJSONArray("seats") ?: continue
            if ((0 until saved.length()).any { saved.optJSONObject(it)?.optString("area") == area.name }) matching.add(item)
        }
        val form = card()
        seatDetailPanel = form
        form.addView(text(name, 28f, bold = true).apply { isAccessibilityHeading = true })
        val detail = text("", 20f, muted)
        seatDetailStatus = detail
        form.addView(detail)
        form.addView(space(12))
        form.addView(text("저장된 프리셋", 22f, bold = true))
        if (matching.isEmpty()) form.addView(text("이 좌석에 저장된 프리셋이 없습니다.", 20f, muted))
        matching.forEach { item ->
            form.addView(space(8))
            form.addView(button(item.optString("name")) { details(item) }, LinearLayout.LayoutParams(-1, -2))
        }
        form.addView(space(12))
        form.addView(button("이 좌석의 현재 자세 저장", primary = true, enabled = !saving) {
            namePopup("현재 자세 저장") { name -> readPositions(name, listOf(area)) }
        }, LinearLayout.LayoutParams(-1, -2))
        form.addView(space(8))
        form.addView(text("현재 프리셋은 조회·저장만 가능합니다. 좌석 이동은 지원되지 않습니다.", 18f, muted))
        form.addView(space(12))
        form.addView(button("닫기") { selectSeat(null) }, LinearLayout.LayoutParams(-1, -2))
        return form
    }
    private fun updateSeatViews() {
        statusView?.text = status
        openedSeat?.let { area ->
            val current = positions[area]
            seatDetailStatus?.text = "전후 ${current?.get("foreAft") ?: "확인 중"} · 등받이 ${current?.get("backrest") ?: "확인 중"}"
        }
    }
    private fun presets(): JSONArray = try { JSONArray(prefs.getString("items", "[]")) } catch (_: Exception) { JSONArray() }
    private fun writePresets(a: JSONArray) { prefs.edit().putString("items", a.toString()).apply() }
    private fun renderPresets(out: LinearLayout) {
        val data = presets()
        if (data.length() == 0) {
            out.addView(card().apply {
                addView(text("저장한 프리셋이 없습니다", 28f, bold = true))
                addView(space(8))
                addView(text("좌석을 선택하고 현재 자세를 저장해 보세요.", 22f, muted))
                addView(space(18))
                addView(button("좌석 상태 보기", primary = true) { navigate(0) }, LinearLayout.LayoutParams(-2, -2))
            })
            return
        }
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i); val c = card(); val r = row(); val info = column()
            info.addView(text(item.optString("name"), 28f, bold = true))
            val saved = item.getJSONArray("seats")
            info.addView(text((0 until saved.length()).joinToString(" · ") { saved.getJSONObject(it).optString("label") }, 20f, muted))
            r.addView(info, weighted())
            r.addView(gap())
            r.addView(button("상세") { details(item) }); c.addView(r); out.addView(c); out.addView(space(10))
        }
    }
    private fun showPopup(builder: AlertDialog.Builder): AlertDialog = builder.show().apply {
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        findViewById<View>(android.R.id.content)?.background = shape(getColor(R.color.seat_popup), 24)
        findViewById<TextView>(android.R.id.message)?.apply {
            typeface = regular
            textSize = 22f
            setTextColor(ink)
        }
        window?.decorView?.let { decor ->
            val titleId = resources.getIdentifier("alertTitle", "id", "android")
            if (titleId != 0) decor.findViewById<TextView>(titleId)?.apply {
                typeface = strong
                textSize = 28f
                setTextColor(ink)
            }
        }
        for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
            getButton(which).apply {
                isAllCaps = false
                typeface = strong
                textSize = 20f
                minimumHeight = dp(64)
                minHeight = dp(64)
                setTextColor(if (which == AlertDialog.BUTTON_POSITIVE) accent else ink)
            }
        }
    }
    private fun details(item: JSONObject) {
        val a = item.getJSONArray("seats")
        val description = (0 until a.length()).joinToString("\n\n") { i -> val s = a.getJSONObject(i); "${s.optString("label")}\n전후 위치: ${if (s.has("foreAft")) s.optString("foreAft") else "미지원"}  ·  등받이 위치: ${if (s.has("backrest")) s.optString("backrest") else "미지원"}" }
        showPopup(AlertDialog.Builder(this).setTitle(item.optString("name")).setMessage("$description\n\n좌석 위치는 조회용입니다.")
            .setPositiveButton("지원 확인") { _, _ -> checkMoveSupport(item) }.setNeutralButton("이름 변경") { _, _ -> rename(item) }
            .setNegativeButton("삭제") { _, _ -> showPopup(AlertDialog.Builder(this).setTitle("프리셋 삭제").setMessage("이 프리셋을 삭제할까요?").setPositiveButton("삭제") { _, _ ->
                val old = presets(); val next = JSONArray(); for (i in 0 until old.length()) if (old.getJSONObject(i).optString("id") != item.optString("id")) next.put(old.getJSONObject(i)); writePresets(next); render()
            }.setNegativeButton("취소", null)) })
    }
    private fun checkMoveSupport(item: JSONObject) {
        val records = item.getJSONArray("seats")
        var fore: List<SeatArea>? = null
        var back: List<SeatArea>? = null
        var foreDone = false; var backDone = false; var shown = false
        val dialog = showPopup(AlertDialog.Builder(this).setTitle("좌석 제어 지원 확인").setMessage("확인 중…").setNegativeButton("닫기", null))
        fun update() {
            if (shown || !foreDone || !backDone || !dialog.isShowing) return
            shown = true
            val lines = (0 until records.length()).map { i ->
                val record = records.getJSONObject(i)
                val area = try { SeatArea(SeatValue.valueOf(record.getString("area"))) } catch (_: Exception) { null }
                val parts = mutableListOf<String>()
                if (record.has("foreAft")) parts.add("전후: " + if (fore == null) "확인 실패" else if (area in fore!!) "지원" else "미지원")
                if (record.has("backrest")) parts.add("등받이: " + if (back == null) "확인 실패" else if (area in back!!) "지원" else "미지원")
                "${record.optString("label")} — ${parts.joinToString(" · ")}"
            }
            dialog.setMessage(lines.joinToString("\n") + "\n\n현재 앱에서는 좌석 위치 조회만 가능합니다.")
        }
        try { vehicle.getSeat().checkMoveSeatForeAftCapabilities({ _, areas -> ui { fore = areas; foreDone = true; update() } }, { ui { foreDone = true; update() } }) }
        catch (_: Exception) { foreDone = true }
        try { vehicle.getSeat().checkMoveSeatBackrestAngleCapabilities({ _, areas -> ui { back = areas; backDone = true; update() } }, { ui { backDone = true; update() } }) }
        catch (_: Exception) { backDone = true }
        update()
        main.postDelayed({ if (!shown && dialog.isShowing) { foreDone = true; backDone = true; update() } }, 8000)
    }
    private fun rename(item: JSONObject) {
        namePopup("프리셋 이름 변경", item.optString("name")) { name ->
            val all = presets()
            for (i in 0 until all.length()) {
                val entry = all.getJSONObject(i)
                if (entry.optString("id") == item.optString("id")) entry.put("name", name)
            }
            writePresets(all)
            render()
        }
    }
    private fun namePopup(title: String, initial: String = "", onSave: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "프리셋 이름"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
            typeface = regular
            textSize = 22f
            minimumHeight = dp(64)
            setText(initial)
            filters = arrayOf(android.text.InputFilter.LengthFilter(30))
        }
        val form = column().apply {
            isFocusableInTouchMode = true
            setPadding(dp(24), dp(12), dp(24), dp(8))
            addView(input, LinearLayout.LayoutParams(-1, -2))
            if (initial.isEmpty()) {
                addView(space(12))
                addView(row().apply {
                    listOf("운전", "휴식", "차박").forEachIndexed { index, suggestion ->
                        if (index > 0) addView(gap())
                        addView(button(suggestion) { input.setText(suggestion) }, weighted())
                    }
                })
            }
            requestFocus()
        }
        val dialog = showPopup(AlertDialog.Builder(this).setTitle(title).setView(form)
            .setNegativeButton("취소", null).setPositiveButton("저장", null))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isEmpty()) {
                input.error = "이름을 입력해 주세요"
                return@setOnClickListener
            }
            dialog.dismiss()
            onSave(name)
        }
    }
    private fun readPositions(name: String, chosen: List<SeatValue>) {
        val result = chosen.associateWith { JSONObject().put("area", it.name).put("label", seats.first { pair -> pair.second == it }.first) }
        var remaining = chosen.size * 2; var successful = 0; var finished = false
        saving = true
        status = "좌석 위치 읽는 중…"
        render()
        val timeout = Runnable { if (!finished) { finished = true; saving = false; status = "좌석 위치 조회 시간 초과"; render(); toast("차량 응답을 확인해 주세요") } }
        main.postDelayed(timeout, 8000)
        fun complete(area: SeatValue, key: String, value: Int?) = ui {
            if (!finished) {
                if (value != null) { result.getValue(area).put(key, value); successful++ }
                remaining--
                if (remaining == 0) {
                    finished = true; saving = false; main.removeCallbacks(timeout)
                    if (successful == 0) { status = "저장할 수 있는 좌석 위치가 없습니다"; render(); toast("차량이 위치 조회를 지원하지 않습니다") }
                    else {
                        val entries = JSONArray(); chosen.forEach { val s = result.getValue(it); if (s.has("foreAft") || s.has("backrest")) entries.put(s) }
                        val a = presets(); a.put(JSONObject().put("id", UUID.randomUUID().toString()).put("name", name).put("seats", entries)); writePresets(a)
                        status = if (successful < chosen.size * 2) "지원되는 위치만 저장됨" else "좌석 위치 저장 완료"
                        render()
                    }
                }
            }
        }
        for (area in chosen) {
            val seatArea = SeatArea(area)
            try { vehicle.getSeat().getCurrentSeatForeAftPosition(seatArea, { complete(area, "foreAft", it) }, { complete(area, "foreAft", null) }) } catch (_: Exception) { complete(area, "foreAft", null) }
            try { vehicle.getSeat().getCurrentSeatBackrestAnglePosition(seatArea, { complete(area, "backrest", it) }, { complete(area, "backrest", null) }) } catch (_: Exception) { complete(area, "backrest", null) }
        }
    }
    private fun refreshSeats() {
        val current = ++generation
        status = "차량 상태 확인 중…"
        seats.forEach { positions[it.second] = mutableMapOf() }
        if (scroll == null) render() else updateSeatViews()
        for ((_, area) in seats) {
            fun position(key: String, value: Int?) = ui {
                if (current == generation) { positions.getValue(area)[key] = value?.toString() ?: "미지원"; finishRefreshIfReady() }
            }
            try { vehicle.getSeat().getCurrentSeatForeAftPosition(SeatArea(area), { position("foreAft", it) }, { position("foreAft", null) }) }
            catch (_: Exception) { position("foreAft", null) }
            try { vehicle.getSeat().getCurrentSeatBackrestAnglePosition(SeatArea(area), { position("backrest", it) }, { position("backrest", null) }) }
            catch (_: Exception) { position("backrest", null) }
        }
        main.postDelayed({ if (current == generation) {
            var timedOut = false
            seats.forEach { (_, area) ->
                for (key in listOf("foreAft", "backrest")) if (positions[area]?.containsKey(key) == false) { positions.getValue(area)[key] = "응답 없음"; timedOut = true }
            }
            if (timedOut) finishRefreshIfReady()
        } }, 8000)
    }
    private fun finishRefreshIfReady() {
        if (!saving && positions.values.all { it.size == 2 }) {
            val incomplete = positions.values.any { entries -> entries.values.any { it == "응답 없음" || it == "미지원" } }
            status = if (incomplete) "일부 좌석 정보를 확인할 수 없습니다" else "차량 상태를 확인했습니다"
        }
        updateSeatViews()
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
