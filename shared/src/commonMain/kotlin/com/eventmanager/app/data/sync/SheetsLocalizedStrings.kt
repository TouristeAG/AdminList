package com.eventmanager.app.data.sync

/**
 * Localized strings written into Google Sheets banner/epoch panels (no Android Context required).
 */
object SheetsLocalizedStrings {
    fun epochHintMs(language: String): String = when (language.substringBefore('-').lowercase()) {
        "fr" -> "Collez des millisecondes epoch (ex. 1712345678901)"
        "es" -> "Pegue milisegundos epoch (ej. 1712345678901)"
        "zh" -> "粘贴 epoch 毫秒（例如 1712345678901）"
        "la" -> "Millisecondas epoch inserite (ex. 1712345678901)"
        "hi" -> "एपॉक मिलीसेकंड पेस्ट करें (उदा. 1712345678901)"
        else -> "Paste epoch milliseconds (e.g. 1712345678901)"
    }

    fun epochHintDateTime(language: String): String = when (language.substringBefore('-').lowercase()) {
        "fr" -> "Collez une date/heure (ex. 2024-04-05)"
        "es" -> "Pegue fecha/hora (ej. 2024-04-05)"
        "zh" -> "粘贴日期/时间（例如 2024-04-05）"
        "la" -> "Diem / horam inserite (ex. 2024-04-05)"
        "hi" -> "तिथि/समय पेस्ट करें (उदा. 2024-04-05)"
        else -> "Paste a date/time (e.g. 2024-04-05)"
    }

    fun epochTitleMsToDate(language: String): String = when (language.substringBefore('-').lowercase()) {
        "fr" -> "Millisecondes → date"
        "es" -> "Milisegundos → fecha"
        "zh" -> "毫秒 → 日期"
        "la" -> "Millisecondae → dies"
        "hi" -> "मिलीसेकंड → तिथि"
        else -> "Milliseconds → date"
    }

    fun epochTitleDateToMs(language: String): String = when (language.substringBefore('-').lowercase()) {
        "fr" -> "Date → millisecondes"
        "es" -> "Fecha → milisegundos"
        "zh" -> "日期 → 毫秒"
        "la" -> "Dies → millisecondae"
        "hi" -> "तिथि → मिलीसेकंड"
        else -> "Date → milliseconds"
    }

    fun volunteerGuestBannerTitle(language: String): String = when (language.substringBefore('-').lowercase()) {
        "fr" -> "Liste invités bénéfices bénévoles"
        "es" -> "Lista de invitados por beneficios de voluntarios"
        "zh" -> "志愿者福利宾客名单"
        "la" -> "Catalogus hospitum beneficiorum voluntariorum"
        "hi" -> "स्वयंसेवक लाभ अतिथि सूची"
        else -> "Volunteer benefit guest list"
    }

    fun volunteerGuestBannerLine(language: String, line: Int): String {
        val lang = language.substringBefore('-').lowercase()
        return when (line) {
            2 -> when (lang) {
                "fr" -> "Ne pas modifier manuellement — synchronisé par l'app"
                "es" -> "No editar manualmente — sincronizado por la app"
                "zh" -> "请勿手动编辑 — 由应用同步"
                "la" -> "Noli manu emendare — ab applicatione synchronizatum"
                "hi" -> "मैन्युअल संपादित न करें — ऐप द्वारा सिंक"
                else -> "Do not edit manually — synced by the app"
            }
            3 -> when (lang) {
                "fr" -> "Une ligne par invité bénéfice"
                "es" -> "Una fila por invitado de beneficio"
                "zh" -> "每位福利宾客一行"
                "la" -> "Una linea per hospitem beneficii"
                "hi" -> "प्रत्येक लाभ अतिथि के लिए एक पंक्ति"
                else -> "One row per benefit guest"
            }
            4 -> when (lang) {
                "fr" -> "NanoID = identifiant unique"
                "es" -> "NanoID = identificador único"
                "zh" -> "NanoID = 唯一标识"
                "la" -> "NanoID = identificator unicus"
                "hi" -> "NanoID = अद्वितीय पहचानकर्ता"
                else -> "NanoID = unique identifier"
            }
            else -> when (lang) {
                "fr" -> "Dernière mise à jour ci-dessous"
                "es" -> "Última actualización abajo"
                "zh" -> "最后更新见下方"
                "la" -> "Ultima renovatio infra"
                "hi" -> "अंतिम अपडेट नीचे"
                else -> "Last update below"
            }
        }
    }

    fun volunteerGuestLastUpdated(language: String, stamp: String): String = when (language.substringBefore('-').lowercase()) {
        "fr" -> "Dernière mise à jour : $stamp"
        "es" -> "Última actualización: $stamp"
        "zh" -> "最后更新：$stamp"
        "la" -> "Ultima renovatio: $stamp"
        "hi" -> "अंतिम अपडेट: $stamp"
        else -> "Last updated: $stamp"
    }
}
