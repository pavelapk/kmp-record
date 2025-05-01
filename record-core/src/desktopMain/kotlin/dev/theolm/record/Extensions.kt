package dev.theolm.record

import dev.theolm.record.config.OutputLocation
import dev.theolm.record.config.RecordConfig
import java.io.File

internal fun getInternalFolder(): String {
    val internalDir = File(System.getProperty("user.home"), ".kmp-record-internal")
    if (!internalDir.exists()) {
        internalDir.mkdirs()
    }
    return internalDir.absolutePath
}

internal fun RecordConfig.getOutput(): String {
    val fileName = "${System.currentTimeMillis()}${outputFormat.extension}"
    return when (outputLocation) {
        OutputLocation.Cache -> "${System.getProperty("java.io.tmpdir")}/$fileName"
        OutputLocation.Internal -> "${getInternalFolder()}/$fileName"
        is OutputLocation.Custom -> "${outputLocation.path}/$fileName"
    }
}
