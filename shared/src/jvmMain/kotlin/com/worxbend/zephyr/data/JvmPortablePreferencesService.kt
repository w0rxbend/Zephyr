package com.worxbend.zephyr.data

import com.worxbend.zephyr.settings.PortablePreferences
import com.worxbend.zephyr.settings.parsePortablePreferences
import com.worxbend.zephyr.settings.renderPortablePreferences
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class JvmPortablePreferencesService : PortablePreferencesService {
    override suspend fun chooseAndRead(): PortablePreferences? = withContext(Dispatchers.IO) {
        val file = onPreferencesEdt {
            JFileChooser().apply {
                dialogTitle = "Import portable Zephyr preferences"
                fileFilter = FileNameExtensionFilter("Zephyr preferences (.zephyr-prefs)", "zephyr-prefs")
            }.let { if (it.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) it.selectedFile else null }
        } ?: return@withContext null
        parsePortablePreferences(Files.readString(file.toPath().toAbsolutePath().normalize()))
    }

    override suspend fun chooseAndWrite(preferences: PortablePreferences): String? = withContext(Dispatchers.IO) {
        val file = onPreferencesEdt {
            JFileChooser().apply {
                dialogTitle = "Export portable Zephyr preferences"
                selectedFile = java.io.File("zephyr.zephyr-prefs")
                fileFilter = FileNameExtensionFilter("Zephyr preferences (.zephyr-prefs)", "zephyr-prefs")
            }.let { chooser ->
                if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) null else {
                    val path = chooser.selectedFile.toPath().toAbsolutePath().normalize()
                    chooser.selectedFile.takeIf {
                        !Files.exists(path) || JOptionPane.showConfirmDialog(
                            null, "${path.fileName} already exists. Replace it?", "Confirm overwrite",
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                        ) == JOptionPane.YES_OPTION
                    }
                }
            }
        } ?: return@withContext null
        val path = file.toPath().toAbsolutePath().normalize()
        Files.writeString(
            path, renderPortablePreferences(preferences),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE,
        )
        path.fileName.toString()
    }
}

actual fun createPortablePreferencesService(): PortablePreferencesService = JvmPortablePreferencesService()

private fun <T> onPreferencesEdt(block: () -> T): T {
    if (javax.swing.SwingUtilities.isEventDispatchThread()) return block()
    val value = java.util.concurrent.atomic.AtomicReference<T>()
    javax.swing.SwingUtilities.invokeAndWait { value.set(block()) }
    return value.get()
}
