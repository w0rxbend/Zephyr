package com.worxbend.zephyr.data

import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class JvmProjectToolchainService : ProjectToolchainService {
    override suspend fun chooseAndRead(): SdkmanRcDocument? = withContext(Dispatchers.IO) {
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose an SDKMAN project file"
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("SDKMAN project files (.sdkmanrc)", "sdkmanrc")
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return@withContext null
        }
        val path = chooser.selectedFile.toPath().toAbsolutePath().normalize()
        require(Files.isRegularFile(path)) { "The selected .sdkmanrc is not a regular file." }
        parseSdkmanRc(path.fileName.toString(), Files.readString(path))
    }
}

actual fun createProjectToolchainService(): ProjectToolchainService = JvmProjectToolchainService()
