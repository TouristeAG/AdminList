package com.eventmanager.app.utils

import com.eventmanager.app.data.models.AccountTransferType
import com.eventmanager.app.data.reports.PosAccountingReport
import com.eventmanager.app.data.reports.PosAccountingReportBuilder
import com.eventmanager.app.data.reports.PosSaleDetail
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import java.awt.Color
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal object PosReportPdfEngine {

    private val GENEVA = TimeZone.getTimeZone("Europe/Zurich")
    private val dateTimeFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).apply { timeZone = GENEVA }
    private val fileFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).apply { timeZone = GENEVA }

    private val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22f)
    private val sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14f)
    private val bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10f)
    private val smallFont = FontFactory.getFont(FontFactory.HELVETICA, 9f)
    private val headerTableFont = Font(Font.HELVETICA, 9f, Font.BOLD, Color.WHITE)

    fun render(report: PosAccountingReport, outputDirectory: File): File {
        outputDirectory.mkdirs()
        val file = File(outputDirectory, "pos_decompte_${fileFmt.format(report.generatedAtMs)}.pdf")
        val document = Document(PageSize.A4, 36f, 36f, 48f, 48f)
        PdfWriter.getInstance(document, FileOutputStream(file))
        document.open()

        addCover(document, report)
        addSummarySection(document, report)
        addTypeSummarySection(document, report)
        addCategorySection(document, report)
        addProductSection(document, report)
        addPosSalesDetail(document, report)
        addManualAdjustments(document, report)
        addShiftTransfers(document, report)
        addFooterNote(document, report)

        document.close()
        return file
    }

    private fun addCover(document: Document, report: PosAccountingReport) {
        document.add(Paragraph("Décompte POS", titleFont).apply { spacingAfter = 12f })
        document.add(Paragraph("Période : ${report.period.label}", bodyFont))
        report.period.closureLabel?.let {
            document.add(Paragraph("Fermeture : $it", bodyFont))
        }
        document.add(Paragraph("Devise : ${report.currencyCode}", bodyFont))
        report.period.venueLabel?.let {
            document.add(Paragraph("Lieu : $it", bodyFont))
        }
        document.add(Paragraph("Généré le : ${dateTimeFmt.format(report.generatedAtMs)}", bodyFont))
        document.add(Paragraph(" ", bodyFont))
    }

    private fun addSummarySection(document: Document, report: PosAccountingReport) {
        section(document, "Synthèse comptable")
        val table = PdfPTable(floatArrayOf(2.5f, 1f)).apply { widthPercentage = 100f }
        row(table, "Nombre de ventes POS", report.totalPosSalesCount.toString())
        row(table, "Encaissements espèces / carte", money(report.totalCashCollected, report.currencyCode))
        row(table, "Crédit interne consommé", money(report.totalCreditUsed, report.currencyCode))
        row(table, "Économies remises bar", money(report.totalBarDiscountSavings, report.currencyCode))
        row(table, "Ajustements manuels (+)", money(report.totalManualPositive, report.currencyCode))
        row(table, "Ajustements manuels (−)", money(report.totalManualNegative, report.currencyCode))
        row(table, "Crédits de shift", money(report.totalShiftCredit, report.currencyCode))
        row(table, "Annulations de shift", money(report.totalShiftReversal, report.currencyCode))
        row(table, "Total transferts", report.totalTransferCount.toString())
        document.add(table)
        document.add(Paragraph(" ", bodyFont))
    }

    private fun addTypeSummarySection(document: Document, report: PosAccountingReport) {
        if (report.typeSummaries.isEmpty()) return
        section(document, "Par type de transfert")
        val table = PdfPTable(floatArrayOf(2f, 0.8f, 1.2f, 1.2f, 1.2f)).apply { widthPercentage = 100f }
        header(table, "Type", "Nb", "Montant", "Crédit", "Espèces")
        report.typeSummaries.forEach { s ->
            row(
                table,
                transferTypeLabel(s.type),
                s.count.toString(),
                money(s.totalAmount, report.currencyCode),
                money(s.creditTotal, report.currencyCode),
                money(s.cashTotal, report.currencyCode),
            )
        }
        document.add(table)
        document.add(Paragraph(" ", bodyFont))
    }

    private fun addCategorySection(document: Document, report: PosAccountingReport) {
        if (report.categorySummaries.isEmpty()) return
        section(document, "Ventes par catégorie")
        val table = PdfPTable(floatArrayOf(2f, 1f, 1.5f)).apply { widthPercentage = 100f }
        header(table, "Catégorie", "Quantité", "Chiffre d'affaires")
        report.categorySummaries.forEach { c ->
            row(table, c.category.name, c.quantity.toString(), money(c.revenue, report.currencyCode))
        }
        document.add(table)
        document.add(Paragraph(" ", bodyFont))
    }

    private fun addProductSection(document: Document, report: PosAccountingReport) {
        if (report.productSummaries.isEmpty()) return
        section(document, "Ventes par produit")
        val table = PdfPTable(floatArrayOf(2.5f, 1f, 1.5f)).apply { widthPercentage = 100f }
        header(table, "Produit", "Quantité", "Chiffre d'affaires")
        report.productSummaries.forEach { p ->
            row(table, p.name, p.quantity.toString(), money(p.revenue, report.currencyCode))
        }
        document.add(table)
        document.add(Paragraph(" ", bodyFont))
    }

    private fun addPosSalesDetail(document: Document, report: PosAccountingReport) {
        if (report.posSales.isEmpty()) return
        section(document, "Détail des ventes POS")
        report.posSales.forEach { sale -> addSaleBlock(document, sale, report.currencyCode) }
    }

    private fun addSaleBlock(document: Document, sale: PosSaleDetail, currency: String) {
        val t = sale.transfer
        val discount = t.posBarDiscountPercent?.let { " · remise $it%" } ?: ""
        document.add(
            Paragraph(
                "${dateTimeFmt.format(t.createdAt)} · ${t.holderName} (${t.holderType.name})$discount",
                bodyFont,
            ).apply { spacingAfter = 4f },
        )
        if (sale.lineItems.isNotEmpty()) {
            val table = PdfPTable(floatArrayOf(2.5f, 0.8f, 1f, 1f)).apply { widthPercentage = 100f }
            header(table, "Article", "Qté", "Prix unit.", "Total")
            sale.lineItems.forEach { line ->
                row(
                    table,
                    line.name,
                    line.quantity.toString(),
                    money(line.unitPrice, currency),
                    money(line.lineTotal, currency),
                )
            }
            document.add(table)
        } else {
            document.add(Paragraph(t.description.ifBlank { "—" }, smallFont))
        }
        document.add(
            Paragraph(
                "Crédit : ${money(sale.creditPaid, currency)}   Espèces/carte : ${money(sale.cashPaid, currency)}   Total : ${money(sale.grossTotal, currency)}",
                smallFont,
            ).apply { spacingAfter = 10f },
        )
    }

    private fun addManualAdjustments(document: Document, report: PosAccountingReport) {
        if (report.manualAdjustments.isEmpty()) return
        section(document, "Ajustements manuels")
        val table = PdfPTable(floatArrayOf(1.5f, 2f, 1.2f, 2.5f)).apply { widthPercentage = 100f }
        header(table, "Date", "Compte", "Montant", "Note")
        report.manualAdjustments.forEach { t ->
            row(
                table,
                dateTimeFmt.format(t.createdAt),
                "${t.holderName} (${t.holderType.name})",
                money(t.amount, report.currencyCode),
                t.description.ifBlank { "—" },
            )
        }
        document.add(table)
        document.add(Paragraph(" ", bodyFont))
    }

    private fun addShiftTransfers(document: Document, report: PosAccountingReport) {
        val rows = report.shiftCredits + report.shiftReversals
        if (rows.isEmpty()) return
        section(document, "Crédits et annulations de shift")
        val table = PdfPTable(floatArrayOf(1.5f, 2f, 1.2f, 1.5f, 2f)).apply { widthPercentage = 100f }
        header(table, "Date", "Bénévole", "Type", "Montant", "Shift")
        rows.sortedBy { it.createdAt }.forEach { t ->
            row(
                table,
                dateTimeFmt.format(t.createdAt),
                t.holderName,
                transferTypeLabel(t.type),
                money(t.amount, report.currencyCode),
                listOf(t.jobTypeName, t.jobDate?.let { dateTimeFmt.format(it) } ?: "")
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "—" },
            )
        }
        document.add(table)
        document.add(Paragraph(" ", bodyFont))
    }

    private fun addFooterNote(document: Document, report: PosAccountingReport) {
        section(document, "Notes méthodologiques")
        val offset = report.period.settingsOffsetHours
        val closure = PosAccountingReportBuilder.formatClosureLabel(
            report.period.closureHour,
            report.period.closureMinute,
        )
        document.add(
            Paragraph(
                "Fuseau horaire : Europe/Zurich. Décalage paramètres : ${if (offset >= 0) "+" else ""}$offset h. " +
                    "Fermeture appliquée : $closure. " +
                    "Tous les montants sont en ${report.currencyCode}. " +
                    "Ce document inclut les ventes POS, ajustements manuels, crédits de shift et annulations.",
                smallFont,
            ),
        )
    }

    private fun section(document: Document, title: String) {
        document.add(Paragraph(title, sectionFont).apply { spacingBefore = 8f; spacingAfter = 6f })
    }

    private fun header(table: PdfPTable, vararg labels: String) {
        labels.forEach { label ->
            val cell = PdfPCell(Phrase(label, headerTableFont)).apply {
                horizontalAlignment = Element.ALIGN_LEFT
                backgroundColor = Color(55, 71, 133)
                paddingTop = 6f
                paddingBottom = 6f
                paddingLeft = 6f
                paddingRight = 6f
            }
            table.addCell(cell)
        }
    }

    private fun row(table: PdfPTable, vararg cells: String) {
        cells.forEachIndexed { index, text ->
            val cell = PdfPCell(Phrase(text, bodyFont)).apply {
                horizontalAlignment = if (index > 0) Element.ALIGN_RIGHT else Element.ALIGN_LEFT
                paddingTop = 4f
                paddingBottom = 4f
                paddingLeft = 6f
                paddingRight = 6f
            }
            table.addCell(cell)
        }
    }

    private fun money(value: Double, currency: String): String =
        String.format(Locale.getDefault(), "%.2f %s", value, currency)

    private fun transferTypeLabel(type: AccountTransferType): String = when (type) {
        AccountTransferType.POS_SALE -> "Vente POS"
        AccountTransferType.MANUAL_ADJUSTMENT -> "Ajustement manuel"
        AccountTransferType.SHIFT_CREDIT -> "Crédit shift"
        AccountTransferType.SHIFT_REVERSAL -> "Annulation shift"
    }
}
