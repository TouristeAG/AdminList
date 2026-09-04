package com.eventmanager.app.utils

/**
 * Android does not ship a StAX implementation. XmlBeans/POI fall back to
 * `com.bea.xml.stream.EventFactory`, which is absent and crashes workbook creation.
 * Point the factories at Woodstox before any [org.apache.poi.xssf.usermodel.XSSFWorkbook] use.
 */
object PoiAndroidInit {
    @Volatile
    private var configured = false

    fun ensureStaxFactories() {
        if (configured) return
        synchronized(this) {
            if (configured) return
            System.setProperty(
                "javax.xml.stream.XMLInputFactory",
                "com.ctc.wstx.stax.WstxInputFactory",
            )
            System.setProperty(
                "javax.xml.stream.XMLOutputFactory",
                "com.ctc.wstx.stax.WstxOutputFactory",
            )
            System.setProperty(
                "javax.xml.stream.XMLEventFactory",
                "com.ctc.wstx.stax.WstxEventFactory",
            )
            configured = true
        }
    }
}
