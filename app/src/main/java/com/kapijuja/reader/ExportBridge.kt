package com.kapijuja.reader

object ExportBridge {
    interface Controller {
        fun cancelExportFromNotification()
    }

    @Volatile
    var controller: Controller? = null
}
