package com.eventmanager.app.utils

import com.eventmanager.app.data.reports.PosAccountingReport
import java.io.File

expect object PosReportPdfRenderer {
    fun render(report: PosAccountingReport, outputDirectory: File): File
}
