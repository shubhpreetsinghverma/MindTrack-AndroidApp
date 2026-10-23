package com.mindtrack.app

import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

/** Builds a report from made-up data, so the layout can be checked without touching real check-ins. */
class ReportSampleTest {
    @Test fun buildsAFullSampleReport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.now()
        val records = (0 until 28).filter { it % 5 != 3 }.map { offset ->
            CheckInRecord(
                date = today.minusDays(offset.toLong()).toString(),
                mood = 2 + (offset * 7 % 4),
                stress = 1 + (offset * 3 % 5),
                energy = 1 + (offset * 5 % 5),
                journal = if (offset % 4 == 0) "A fairly ordinary day. Work was busy but I took a walk in the evening." else "",
                tags = if (offset % 3 == 0) listOf("Good sleep", "Exercise") else listOf("Work or study")
            )
        }
        val file = ReportExporter.create(context, records, includeNotes = true, name = "Sample Person")
        assertTrue(file.length() > 5_000)
    }
}
