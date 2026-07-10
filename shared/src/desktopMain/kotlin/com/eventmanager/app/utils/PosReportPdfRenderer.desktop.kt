package com.eventmanager.app.utils

import com.eventmanager.app.data.reports.PosAccountingReport
import java.io.File

actual object PosReportPdfRenderer {
    actual fun render(report: PosAccountingReport, outputDirectory: File): File =
        PosReportPdfEngine.render(report, outputDirectory)
}
