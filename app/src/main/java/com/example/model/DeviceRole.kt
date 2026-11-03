package com.example.model

enum class DeviceRole(val titleFa: String, val titleEn: String, val descriptionFa: String) {
    MONITOR_AND_ALARM(
        titleFa = "گیرنده و هشداردهنده (Listener)",
        titleEn = "Alarm Receiver",
        descriptionFa = "این گوشی ارتباط را زیر نظر می‌گیرد و با قطع اینترنت یا جابجایی طرف مقابل، آژیر می‌کشد."
    ),
    MONITORED_SENDER(
        titleFa = "تحت پایش (Sender)",
        titleEn = "Monitored Device",
        descriptionFa = "این گوشی ضربان اتصال و موقعیت خود را ارسال می‌کند تا پایداری و ثبات آن سنجیده شود."
    ),
    MUTUAL(
        titleFa = "پایش دوطرفه (Mutual 2-Way)",
        titleEn = "Mutual Both-Ways",
        descriptionFa = "هر دو گوشی هم ضربان و موقعیت می‌فرستند و هم در صورت قطعی یا جابجایی طرف مقابل زنگ می‌زنند."
    )
}

enum class ConnectionStatus {
    IDLE,
    CONNECTING,
    ONLINE_MONITORING,
    DISCONNECTED_ALERT,
    LOCATION_CHANGED_ALERT,
    MUTED_ALERT,
    RECONNECTING
}
