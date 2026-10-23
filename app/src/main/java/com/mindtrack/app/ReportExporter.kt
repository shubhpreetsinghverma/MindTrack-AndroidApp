package com.mindtrack.app

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the shareable PDF report. The only branding is the app name: a faint "MindTrack"
 * watermark tiled across every page, plus the header and footer text.
 */
internal object ReportExporter {

    fun create(
        context: Context,
        records: List<CheckInRecord>,
        includeNotes: Boolean,
        name: String
    ): File {
        val stats = buildReportStats(records) ?: error("There are no check-ins to export.")
        reportPixelFont = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, R.font.pixelify_sans) }.getOrNull()

        // First pass only counts pages, so the footer can say "Page 2 of 5".
        val counter = ReportRenderer(stats, includeNotes, name, totalPages = 0)
        val total = counter.render().also { counter.close() }

        val renderer = ReportRenderer(stats, includeNotes, name, totalPages = total)
        renderer.render()

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "MindTrack-report-${LocalDate.now()}.pdf")
        file.outputStream().use { renderer.writeTo(it) }
        renderer.close()
        return file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MindTrack wellbeing report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private const val PAGE_W = 595
private const val PAGE_H = 842
private const val MARGIN = 44f
private const val CONTENT_W = PAGE_W - 2 * MARGIN
private const val BOTTOM_LIMIT = PAGE_H - 58f

/** Set while a report is being made, so the small labels use the app's pixel font. */
private var reportPixelFont: Typeface? = null

private val INK = Color.parseColor("#1C1C1C")
private val MUTED = Color.parseColor("#585858")
private val ACCENT = Color.parseColor("#6B5FB3")
private val ACCENT_SOFT = Color.parseColor("#F1EEE6")
private val PAPER = Color.parseColor("#FBFAF6")
private val BORDER = Color.parseColor("#B4B4B4")
private val GRID = Color.parseColor("#DEDEDE")
private val STRESS_COLOR = Color.parseColor("#B5651D")
private val ENERGY_COLOR = Color.parseColor("#2F7D6B")
private val MOOD_COLORS = mapOf(
    1 to Color.parseColor("#D9667A"), 2 to Color.parseColor("#E49A6B"), 3 to Color.parseColor("#E2C15A"),
    4 to Color.parseColor("#7FC59A"), 5 to Color.parseColor("#4DAF86")
)
private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val SHORT_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

private fun moodLabel(score: Int) = moodOptions.firstOrNull { it.score == score }?.label ?: "—"
private fun one(v: Double) = String.format(Locale.ENGLISH, "%.1f", v)

private fun tp(color: Int, size: Float, style: Int = Typeface.NORMAL) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    textSize = size
    // Android has no Times New Roman; its serif family is the closest built-in match.
    typeface = Typeface.create(Typeface.SERIF, style)
}

/** Small wide-spaced labels, in a monospace face to echo the app's pixel labels. */
private fun mono(color: Int, size: Float, style: Int = Typeface.NORMAL) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    textSize = size
    letterSpacing = 0.12f
    typeface = reportPixelFont ?: Typeface.create(Typeface.MONOSPACE, style)
}

/** The app's little pixel moon, drawn from the same cells the screens use. */
private fun drawPixelMoon(canvas: Canvas, left: Float, top: Float, size: Float) {
    val cell = size / 12f
    val body = fill(Color.parseColor("#8E83C9"))
    moonCells.forEach { (x, y) -> canvas.drawRect(left + x * cell, top + y * cell, left + (x + 1) * cell, top + (y + 1) * cell, body) }
    val ink = fill(Color.parseColor("#2A2733"))
    moonEye(MoonMood.SLEEPY).forEach { (x, y) -> canvas.drawRect(left + x * cell, top + y * cell, left + (x + 1) * cell, top + (y + 1) * cell, ink) }
    val blush = fill(Color.parseColor("#F2A6B8"))
    canvas.drawRect(left + 2 * cell, top + 8 * cell, left + 3 * cell, top + 9 * cell, blush)
}

private fun fill(color: Int, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color; this.alpha = alpha; style = Paint.Style.FILL
}

private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color; strokeWidth = width; style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
}

private class ReportRenderer(
    private val stats: ReportStats,
    private val includeNotes: Boolean,
    private val name: String,
    private val totalPages: Int
) {
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas = Canvas()
    private var pageNo = 0
    private var y = 0f

    private val body = tp(INK, 10.5f)
    private val small = tp(MUTED, 9f)
    private val label = mono(MUTED, 8.5f)
    private val h1 = tp(ACCENT, 15f, Typeface.BOLD)

    fun close() = doc.close()
    fun writeTo(out: java.io.OutputStream) = doc.writeTo(out)

    /** Lays out the whole report and returns the number of pages. */
    fun render(): Int {
        startPage(first = true)
        drawCover()
        aboutAndDisclaimer()
        summaryTiles()
        scaleLegend()
        moodChartSection()
        stressEnergySection()
        distributionSection()
        if (stats.records.size >= 7 && stats.weekdayMood.size >= 3) weekdaySection()
        observationsSection()
        dailyLogSection()
        doctorSection()
        endPage()
        return pageNo
    }

    // ---- page handling ----------------------------------------------------------------

    private fun startPage(first: Boolean = false) {
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        canvas = page!!.canvas
        canvas.drawColor(PAPER)
        if (first) {
            y = MARGIN
        } else {
            drawPixelMoon(canvas, MARGIN, 20f, 16f)
            canvas.drawText("MINDTRACK", MARGIN + 22f, 33f, mono(ACCENT, 9.5f, Typeface.BOLD))
            val rightHead = mono(MUTED, 9f).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("WELLBEING REPORT", PAGE_W - MARGIN, 33f, rightHead)
            canvas.drawLine(MARGIN, 42f, PAGE_W - MARGIN, 42f, stroke(INK, 0.6f))
            y = 62f
        }
    }

    private fun endPage() {
        drawFooter()
        drawWatermark()
        doc.finishPage(page)
        page = null
    }

    private fun ensure(height: Float) {
        if (y + height > BOTTOM_LIMIT) {
            endPage()
            startPage()
        }
    }

    private fun drawFooter() {
        canvas.drawLine(MARGIN, PAGE_H - 44f, PAGE_W - MARGIN, PAGE_H - 44f, stroke(INK, 0.6f))
        canvas.drawText("MindTrack  ·  Self-reported wellbeing summary, not a clinical assessment", MARGIN, PAGE_H - 28f, tp(MUTED, 8.5f, Typeface.ITALIC))
        val right = mono(MUTED, 8.5f).apply { textAlign = Paint.Align.RIGHT }
        val of = if (totalPages > 0) " of $totalPages" else ""
        canvas.drawText("Page $pageNo$of", PAGE_W - MARGIN, PAGE_H - 28f, right)
    }

    /** The app name, tiled diagonally across the whole page. */
    private fun drawWatermark() {
        val paint = tp(Color.parseColor("#7A7A7A"), 32f, Typeface.BOLD_ITALIC).apply {
            alpha = 22
            textAlign = Paint.Align.CENTER
        }
        canvas.save()
        canvas.rotate(-28f, PAGE_W / 2f, PAGE_H / 2f)
        var row = 0
        var yy = -320f
        while (yy < PAGE_H + 320f) {
            var xx = -320f + if (row % 2 == 0) 0f else 130f
            while (xx < PAGE_W + 320f) {
                canvas.drawText("MindTrack", xx, yy, paint)
                xx += 260f
            }
            yy += 118f
            row++
        }
        canvas.restore()
    }

    // ---- text helpers -----------------------------------------------------------------

    private fun layout(text: CharSequence, paint: TextPaint, width: Float) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setLineSpacing(0f, 1.18f)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()

    /** Draws wrapped text at the current y and moves down. */
    private fun paragraph(text: String, paint: TextPaint = body, x: Float = MARGIN, width: Float = CONTENT_W, after: Float = 8f) {
        val l = layout(text, paint, width)
        ensure(l.height.toFloat())
        canvas.save(); canvas.translate(x, y); l.draw(canvas); canvas.restore()
        y += l.height + after
    }

    private var section = 0

    private fun heading(title: String) {
        ensure(96f)
        y += 12f
        canvas.drawText("${++section}.  $title", MARGIN, y + 13.5f, h1)
        canvas.drawLine(MARGIN, y + 20f, PAGE_W - MARGIN, y + 20f, stroke(ACCENT, 0.9f))
        y += 32f
    }

    /** "What this shows / How to read it / In your data" block used under every graphic. */
    /** A small caption plus its paragraph, kept together so a caption is never left alone at the bottom of a page. */
    private fun block(title: String, text: String, after: Float) {
        val l = layout(text, body, CONTENT_W)
        ensure(l.height + 14f)
        canvas.drawText(title, MARGIN, y + 8f, label)
        y += 13f
        paragraph(text, after = after)
    }

    /** "What this shows / How to read it / In your data" block used under every graphic. */
    private fun explain(what: String, how: String, data: String?) {
        block("WHAT THIS SHOWS", what, 6f)
        block("HOW TO READ IT", how, 6f)
        if (data != null) block("IN YOUR DATA", data, 10f)
    }

    private fun box(height: Float, fillColor: Int, borderColor: Int, drawInside: (RectF) -> Unit) {
        ensure(height)
        val r = RectF(MARGIN, y, PAGE_W - MARGIN, y + height)
        canvas.drawRect(r, fill(fillColor))
        canvas.drawRect(r, stroke(borderColor, 0.9f))
        drawInside(r)
        y += height + 12f
    }

    // ---- sections ---------------------------------------------------------------------

    private fun drawCover() {
        // A plain letterhead: name, rule, title, who and when.
        drawPixelMoon(canvas, MARGIN, y - 2f, 26f)
        canvas.drawText("MINDTRACK", MARGIN + 34f, y + 13f, mono(ACCENT, 13f, Typeface.BOLD))
        val tag = mono(MUTED, 9f).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("A PERSONAL WELLBEING RECORD", PAGE_W - MARGIN, y + 13f, tag)
        y += 30f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, stroke(ACCENT, 1.6f))
        canvas.drawLine(MARGIN, y + 3.5f, PAGE_W - MARGIN, y + 3.5f, stroke(ACCENT, 0.5f))
        y += 40f

        canvas.drawText("Wellbeing Report", MARGIN, y, tp(INK, 30f, Typeface.BOLD))
        y += 24f
        val period = "${stats.from.format(DATE_FMT)} to ${stats.to.format(DATE_FMT)}"
        canvas.drawText("Prepared for $name", MARGIN, y, tp(INK, 12.5f))
        y += 17f
        canvas.drawText("Period covered: $period", MARGIN, y, tp(MUTED, 11f))
        y += 15f
        canvas.drawText("Generated on ${LocalDate.now().format(DATE_FMT)}", MARGIN, y, tp(MUTED, 11f, Typeface.ITALIC))
        y += 16f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, stroke(BORDER, 0.8f))
        y += 14f
    }

    private fun aboutAndDisclaimer() {
        heading("About this report")
        paragraph(
            "This report summarises the mood, stress and energy ratings recorded in the MindTrack app between " +
                "${stats.from.format(DATE_FMT)} and ${stats.to.format(DATE_FMT)}. On each check-in the person rates mood and energy " +
                "from 1 (lowest) to 5 (highest) and stress from 1 (calm) to 5 (very stressed), and may add a written note. " +
                "Every value is self-reported: MindTrack has not measured, tested or verified anything, and the charts show only what was entered."
        )

        val text = "This report is a self-reported wellbeing summary. It is not a clinical assessment, diagnosis or medical advice, " +
            "and it does not replace professional care. Please share it with a qualified doctor or mental health professional, " +
            "who can interpret it alongside your history and an examination, and have its contents cross-verified by them. " +
            "If you feel unsafe or may be in danger, contact your local emergency number (112 in India, 911 in the US, 999 in the UK) straight away."
        val l = layout(text, tp(INK, 10.5f), CONTENT_W - 28f)
        box(l.height + 46f, ACCENT_SOFT, INK) { r ->
            canvas.drawText("Important notice", r.left + 14f, r.top + 20f, tp(INK, 11f, Typeface.BOLD))
            canvas.save(); canvas.translate(r.left + 14f, r.top + 30f); l.draw(canvas); canvas.restore()
        }
    }

    private fun summaryTiles() {
        heading("Summary at a glance")
        paragraph(
            "These six figures describe the whole period. Averages use only the days on which a check-in was saved, so missed days do not pull them up or down.",
            after = 10f
        )
        val rows = listOf(
            Triple("Check-ins saved", "${stats.records.size}", "days with a saved entry"),
            Triple("Period covered", "${stats.daysCovered} days", "first entry to last entry"),
            Triple("Consistency", "${stats.consistencyPercent}%", "of days in the period checked in"),
            Triple("Average mood", "${one(stats.averageMood)} / 5", "5 = Great, 1 = Rough"),
            Triple("Average stress", "${one(stats.averageStress)} / 5", "higher means more stress"),
            Triple("Average energy", "${one(stats.averageEnergy)} / 5", "higher means more energy")
        )
        val rowH = 24f
        ensure(rows.size * rowH + 10f)
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, stroke(INK, 1.1f))
        rows.forEach { (title, value, caption) ->
            canvas.drawText(title, MARGIN + 6f, y + 16f, tp(INK, 10.5f, Typeface.BOLD))
            canvas.drawText(value, MARGIN + 170f, y + 16f, tp(ACCENT, 12f, Typeface.BOLD))
            canvas.drawText(caption, MARGIN + 270f, y + 16f, tp(MUTED, 9.5f, Typeface.ITALIC))
            y += rowH
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, stroke(GRID, 0.8f))
        }
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, stroke(INK, 1.1f))
        y += 12f
        paragraph(
            "Longest unbroken run of daily check-ins: ${stats.longestStreak} day${if (stats.longestStreak == 1) "" else "s"}.",
            small, after = 4f
        )
    }

    private fun scaleLegend() {
        heading("Reading the mood scale")
        paragraph("Mood is recorded on five levels. The same colours are used in the charts and the daily log that follow.", after = 8f)
        ensure(30f)
        val w = CONTENT_W / 5f
        (1..5).forEach { score ->
            val x = MARGIN + (score - 1) * w
            canvas.drawCircle(x + 8f, y + 8f, 6f, fill(MOOD_COLORS.getValue(score)))
            canvas.drawText("$score  ${moodLabel(score)}", x + 20f, y + 12f, tp(INK, 9.5f))
        }
        y += 30f
    }

    // ---- charts -----------------------------------------------------------------------

    private class Series(val name: String, val color: Int, val values: List<Pair<LocalDate, Int>>)

    private fun lineChart(height: Float, series: List<Series>, yLabels: List<String>, colourByValue: Boolean) {
        ensure(height + 8f)
        val left = MARGIN + 50f
        val right = PAGE_W - MARGIN - 10f
        val top = y + 8f
        val bottom = y + height - 24f
        fun yFor(v: Int) = bottom - (v - 1) / 4f * (bottom - top)
        val spanDays = (stats.to.toEpochDay() - stats.from.toEpochDay()).coerceAtLeast(1)
        fun xFor(d: LocalDate) =
            if (stats.from == stats.to) (left + right) / 2f
            else left + (d.toEpochDay() - stats.from.toEpochDay()).toFloat() / spanDays * (right - left)

        val frame = RectF(MARGIN, y, PAGE_W - MARGIN, y + height)
        canvas.drawRect(frame, fill(Color.WHITE, 200))
        canvas.drawRect(frame, stroke(BORDER, 0.9f))

        val axis = tp(MUTED, 7.5f).apply { textAlign = Paint.Align.RIGHT }
        (1..5).forEach { v ->
            canvas.drawLine(left, yFor(v), right, yFor(v), stroke(GRID, 0.8f))
            canvas.drawText(yLabels[v - 1], left - 6f, yFor(v) + 2.7f, axis)
        }

        val pointR = if (stats.records.size > 45) 2f else 3.2f
        series.forEach { s ->
            val sorted = s.values.sortedBy { it.first }
            val path = Path()
            sorted.forEachIndexed { i, (d, v) -> if (i == 0) path.moveTo(xFor(d), yFor(v)) else path.lineTo(xFor(d), yFor(v)) }
            if (sorted.size > 1) canvas.drawPath(path, stroke(s.color, 1.4f))
            sorted.forEach { (d, v) ->
                val c = if (colourByValue) MOOD_COLORS.getValue(v) else s.color
                canvas.drawCircle(xFor(d), yFor(v), pointR, fill(c))
                canvas.drawCircle(xFor(d), yFor(v), pointR, stroke(Color.WHITE, 0.8f))
            }
        }

        val dateP = tp(MUTED, 8f)
        canvas.drawText(stats.from.format(SHORT_FMT), left, bottom + 15f, dateP)
        if (stats.daysCovered > 10) {
            dateP.textAlign = Paint.Align.CENTER
            canvas.drawText(stats.from.plusDays(spanDays / 2).format(SHORT_FMT), (left + right) / 2f, bottom + 15f, dateP)
        }
        dateP.textAlign = Paint.Align.RIGHT
        canvas.drawText(stats.to.format(SHORT_FMT), right, bottom + 15f, dateP)
        y += height + 10f
    }

    private fun moodChartSection() {
        heading("Mood over time")
        val data = stats.records.map { LocalDate.parse(it.date) to it.mood }
        lineChart(190f, listOf(Series("Mood", ACCENT, data)), (1..5).map { "${moodLabel(it)} $it" }, colourByValue = true)
        val best = stats.records.maxOf { it.mood }
        val low = stats.records.minOf { it.mood }
        explain(
            what = "A line chart with one dot for every day a check-in was saved. The dot's height is that day's mood rating and its colour matches the mood scale above.",
            how = "Left to right is oldest to newest. Dots near the top are better days and dots near the bottom are harder days. A line that drifts upward suggests mood improving over the period, downward the opposite, and a flat line a steady period. Single low days are normal and are not a pattern by themselves.",
            data = "Average mood was ${one(stats.averageMood)} out of 5. The highest rating recorded was $best (${moodLabel(best)}) and the lowest was $low (${moodLabel(low)})."
        )
    }

    private fun stressEnergySection() {
        ensure(240f)   // heading, key and chart stay together
        heading("Stress and energy over time")
        ensure(18f)
        listOf("Stress" to STRESS_COLOR, "Energy" to ENERGY_COLOR).forEachIndexed { i, (n, c) ->
            val x = MARGIN + i * 90f
            canvas.drawCircle(x + 5f, y + 6f, 4.5f, fill(c))
            canvas.drawText(n, x + 15f, y + 9.5f, tp(INK, 9.5f, Typeface.BOLD))
        }
        y += 18f
        lineChart(
            170f,
            listOf(
                Series("Stress", STRESS_COLOR, stats.records.map { LocalDate.parse(it.date) to it.stress }),
                Series("Energy", ENERGY_COLOR, stats.records.map { LocalDate.parse(it.date) to it.energy })
            ),
            (1..5).map { "$it" }, colourByValue = false
        )
        explain(
            what = "Two lines on the same 1 to 5 scale: the orange line is stress and the green line is energy, each recorded on the same check-ins as mood.",
            how = "For stress, a higher point means more pressure that day; for energy, a higher point means more energy. Looking at the two lines together can show whether low-energy stretches line up with high-stress stretches. This only describes what was entered and cannot say why either changed.",
            data = "Average stress was ${one(stats.averageStress)} and average energy was ${one(stats.averageEnergy)} out of 5."
        )
    }

    private fun distributionSection() {
        heading("How often each mood was recorded")
        val n = stats.records.size
        val labelW = 84f
        val maxBar = CONTENT_W - labelW - 78f
        val maxCount = stats.moodCounts.values.max().coerceAtLeast(1)
        ensure(5 * 24f + 10f)
        (5 downTo 1).forEach { score ->
            val count = stats.moodCounts.getValue(score)
            canvas.drawText("${moodLabel(score)} ($score)", MARGIN, y + 11f, tp(INK, 9.5f))
            canvas.drawRoundRect(RectF(MARGIN + labelW, y + 1f, MARGIN + labelW + maxBar, y + 15f), 1.5f, 1.5f, fill(GRID))
            if (count > 0) {
                canvas.drawRoundRect(
                    RectF(MARGIN + labelW, y + 1f, MARGIN + labelW + maxBar * count / maxCount, y + 15f),
                    1.5f, 1.5f, fill(MOOD_COLORS.getValue(score))
                )
            }
            canvas.drawText("$count  (${(count * 100.0 / n).toInt()}%)", MARGIN + labelW + maxBar + 8f, y + 11f, tp(MUTED, 9f))
            y += 24f
        }
        y += 6f
        val mostCommon = stats.moodCounts.maxByOrNull { it.value }!!.key
        explain(
            what = "A bar for each of the five mood levels, showing how many check-ins fell into it.",
            how = "A longer bar means that mood was recorded more often. The number on the right is the count and its share of all check-ins. A cluster of long bars near the bottom or top shows where most days sat; spread across several bars shows a more variable period.",
            data = "The most frequently recorded mood was ${moodLabel(mostCommon)} (${stats.moodCounts.getValue(mostCommon)} of $n check-ins)."
        )
    }

    private fun weekdaySection() {
        heading("Mood by day of the week")
        val chartH = 110f
        ensure(chartH + 30f)
        val colW = CONTENT_W / 7f
        val baseY = y + chartH - 18f
        val maxH = chartH - 40f
        DayOfWeek.entries.forEachIndexed { i, day ->
            val x = MARGIN + i * colW
            val avg = stats.weekdayMood[day]
            canvas.drawRoundRect(RectF(x + 10f, baseY - maxH, x + colW - 10f, baseY), 1.5f, 1.5f, fill(GRID, 160))
            if (avg != null) {
                val h = maxH * (avg / 5.0).toFloat()
                canvas.drawRoundRect(RectF(x + 10f, baseY - h, x + colW - 10f, baseY), 1.5f, 1.5f, fill(ACCENT))
                val c = tp(INK, 8.5f, Typeface.BOLD).apply { textAlign = Paint.Align.CENTER }
                canvas.drawText(one(avg), x + colW / 2f, baseY - h - 4f, c)
            } else {
                val c = tp(MUTED, 8.5f).apply { textAlign = Paint.Align.CENTER }
                canvas.drawText("–", x + colW / 2f, baseY - 6f, c)
            }
            val l = tp(MUTED, 8.5f).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText(day.pretty().take(3), x + colW / 2f, baseY + 13f, l)
        }
        y += chartH + 8f
        explain(
            what = "Seven bars, one for each weekday, showing the average mood rating on that weekday across the whole period.",
            how = "A taller bar means higher average mood on that weekday; a dash means no check-ins fell on it. Differences between bars can reflect routines such as work or study days, but with a small number of entries per weekday they may simply be chance.",
            data = null
        )
    }

    private fun observationsSection() {
        ensure(160f)
        heading("Observations from the numbers")
        paragraph("These sentences are generated automatically from the figures above. They describe the data only; they do not diagnose anything or suggest causes.", small, after = 8f)
        describeObservations(stats).forEach { line ->
            val l = layout(line, body, CONTENT_W - 16f)
            ensure(l.height.toFloat())
            canvas.drawCircle(MARGIN + 4f, y + 6f, 2.4f, fill(ACCENT))
            canvas.save(); canvas.translate(MARGIN + 16f, y); l.draw(canvas); canvas.restore()
            y += l.height + 5f
        }
        y += 4f
    }

    private fun dailyLogSection() {
        heading("Daily log")
        paragraph(
            "Every saved check-in, oldest first." + if (includeNotes) " Tags and written notes appear in italics beneath the day they belong to." else " Tags appear beneath each day; written notes were left out of this report.",
            after = 8f
        )
        val cols = floatArrayOf(MARGIN + 8f, MARGIN + 150f, MARGIN + 290f, MARGIN + 380f)
        fun header() {
            ensure(34f)
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, stroke(INK, 1.1f))
            val w = mono(INK, 8.5f, Typeface.BOLD)
            listOf("DATE", "MOOD", "STRESS (1-5)", "ENERGY (1-5)").forEachIndexed { i, t -> canvas.drawText(t, cols[i], y + 14f, w) }
            canvas.drawLine(MARGIN, y + 20f, PAGE_W - MARGIN, y + 20f, stroke(INK, 0.6f))
            y += 24f
        }
        header()
        stats.records.forEachIndexed { index, r ->
            val extra = buildString {
                if (r.tags.isNotEmpty()) append("Tags: ${r.tags.joinToString(", ")}")
                if (includeNotes && r.journal.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("Note: ${r.journal.trim()}")
                }
            }
            val note = if (extra.isNotEmpty()) layout(extra, tp(MUTED, 9f, Typeface.ITALIC), CONTENT_W - 24f) else null
            val rowH = 20f + (note?.height?.toFloat()?.plus(6f) ?: 0f)
            if (y + rowH > BOTTOM_LIMIT) { endPage(); startPage(); header() }
            if (index % 2 == 0) canvas.drawRect(RectF(MARGIN, y - 2f, PAGE_W - MARGIN, y + rowH - 2f), fill(ACCENT_SOFT, 220))
            val d = LocalDate.parse(r.date)
            canvas.drawText("${d.format(DATE_FMT)}", cols[0], y + 11f, tp(INK, 9.5f))
            canvas.drawCircle(cols[1] + 5f, y + 8f, 4.5f, fill(MOOD_COLORS.getValue(r.mood)))
            canvas.drawText("${moodLabel(r.mood)} (${r.mood})", cols[1] + 16f, y + 11f, tp(INK, 9.5f))
            canvas.drawText("${r.stress}", cols[2], y + 11f, tp(INK, 9.5f))
            canvas.drawText("${r.energy}", cols[3], y + 11f, tp(INK, 9.5f))
            if (note != null) {
                canvas.save(); canvas.translate(MARGIN + 12f, y + 17f); note.draw(canvas); canvas.restore()
            }
            y += rowH
        }
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, stroke(INK, 1.1f))
        y += 10f
    }

    private fun doctorSection() {
        ensure(310f)   // keep the questions and the closing note together on one page
        heading("Questions you may wish to discuss with a doctor")
        paragraph("Bring this report to an appointment. It can help a professional see how things have been between visits. Some starting points:", after = 6f)
        listOf(
            "Do the changes in my mood, stress or energy over this period concern you?",
            "Could anything in my sleep, routine, health or medication be affecting these ratings?",
            "Is there a pattern here that suggests I should have a fuller assessment?",
            "What support, habits or follow-up would you recommend for me?"
        ).forEach { q ->
            val l = layout(q, body, CONTENT_W - 16f)
            ensure(l.height.toFloat())
            canvas.drawCircle(MARGIN + 4f, y + 6f, 2.4f, fill(ACCENT))
            canvas.save(); canvas.translate(MARGIN + 16f, y); l.draw(canvas); canvas.restore()
            y += l.height + 5f
        }
        y += 8f
        val closing = "MindTrack is a self-reflection tool. This document is a summary of self-reported data, not a clinical assessment, " +
            "diagnosis or treatment plan, and it is not a replacement for professional medical care. Please get it reviewed and " +
            "cross-verified by a qualified doctor before relying on anything in it."
        val l = layout(closing, tp(INK, 9.5f), CONTENT_W - 28f)
        box(l.height + 30f, ACCENT_SOFT, INK) { r ->
            canvas.save(); canvas.translate(r.left + 14f, r.top + 15f); l.draw(canvas); canvas.restore()
        }
    }
}
