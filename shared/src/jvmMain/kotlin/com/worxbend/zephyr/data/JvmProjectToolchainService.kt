package com.worxbend.zephyr.data

import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class JvmProjectToolchainService : ProjectToolchainService {
    override suspend fun chooseAndRead(): SdkmanRcDocument? = withContext(Dispatchers.IO) {
        val selectedFile = onEdt {
            val chooser = JFileChooser().apply {
                dialogTitle = "Choose an SDKMAN project file"
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = FileNameExtensionFilter("SDKMAN project files (.sdkmanrc)", "sdkmanrc")
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        }
            ?: return@withContext null
        val path = selectedFile.toPath().toAbsolutePath().normalize()
        require(Files.isRegularFile(path)) { "The selected .sdkmanrc is not a regular file." }
        parseSdkmanRc(path.fileName.toString(), Files.readString(path))
    }

    override suspend fun chooseAndWrite(targets: List<com.worxbend.zephyr.domain.InstallTarget>): SdkmanRcExportResult? =
        withContext(Dispatchers.IO) {
            require(targets.isNotEmpty()) { "Select at least one default to export." }
            val selectedFile = onEdt {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Export SDKMAN project file"
                    selectedFile = java.io.File(".sdkmanrc")
                }
                if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
                    null
                } else {
                    val path = chooser.selectedFile.toPath().toAbsolutePath().normalize()
                    val confirmed = !Files.exists(path) || JOptionPane.showConfirmDialog(
                        null,
                        "${path.fileName} already exists. Replace it?",
                        "Confirm overwrite",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                    ) == JOptionPane.YES_OPTION
                    chooser.selectedFile.takeIf { confirmed }
                }
            }
                ?: return@withContext null
            val path = selectedFile.toPath().toAbsolutePath().normalize()
            Files.writeString(
                path,
                renderSdkmanRc(targets),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            SdkmanRcExportResult(path.fileName.toString(), targets.size)
        }
}

actual fun createProjectToolchainService(): ProjectToolchainService = JvmProjectToolchainService()

private fun <T> onEdt(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    val result = AtomicReference<T>()
    SwingUtilities.invokeAndWait { result.set(block()) }
    return result.get()
}
