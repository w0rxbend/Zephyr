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

internal class JvmEnvironmentSnapshotService : EnvironmentSnapshotService {
    override suspend fun chooseAndRead(): EnvironmentSnapshot? = withContext(Dispatchers.IO) {
        val selectedFile = snapshotOnEdt {
            val chooser = JFileChooser().apply {
                dialogTitle = "Choose a Zephyr environment snapshot"
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = FileNameExtensionFilter("Zephyr snapshots (.zephyr-snapshot)", "zephyr-snapshot")
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        } ?: return@withContext null
        val path = selectedFile.toPath().toAbsolutePath().normalize()
        require(Files.isRegularFile(path)) { "The selected snapshot is not a regular file." }
        parseEnvironmentSnapshot(Files.readString(path))
    }

    override suspend fun chooseAndWrite(snapshot: EnvironmentSnapshot): EnvironmentSnapshotExportResult? =
        withContext(Dispatchers.IO) {
            require(snapshot.candidates.isNotEmpty()) { "There are no installed versions or defaults to export." }
            val selectedFile = snapshotOnEdt {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Export Zephyr environment snapshot"
                    selectedFile = java.io.File("environment.zephyr-snapshot")
                    fileFilter = FileNameExtensionFilter("Zephyr snapshots (.zephyr-snapshot)", "zephyr-snapshot")
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
            } ?: return@withContext null
            val path = selectedFile.toPath().toAbsolutePath().normalize()
            Files.writeString(
                path,
                renderEnvironmentSnapshot(snapshot),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            EnvironmentSnapshotExportResult(
                fileName = path.fileName.toString(),
                candidateCount = snapshot.candidates.size,
                versionCount = snapshot.candidates.sumOf { it.installedVersions.size },
            )
        }
}

actual fun createEnvironmentSnapshotService(): EnvironmentSnapshotService = JvmEnvironmentSnapshotService()

private fun <T> snapshotOnEdt(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    val result = AtomicReference<T>()
    SwingUtilities.invokeAndWait { result.set(block()) }
    return result.get()
}
