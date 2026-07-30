package com.worxbend.zephyr.data

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.worxbend.zephyr.settings.ProjectWorkspaceReference

internal class JvmProjectToolchainService : ProjectToolchainService {
    override suspend fun chooseAndRead(): SdkmanRcDocument? = withContext(Dispatchers.IO) {
        val path = chooseSdkmanRcPath() ?: return@withContext null
        readWorkspace(referenceFor(path)).sdkmanRc
    }

    override suspend fun chooseWorkspace(): ProjectWorkspaceDocument? = withContext(Dispatchers.IO) {
        val path = chooseSdkmanRcPath() ?: return@withContext null
        readWorkspace(referenceFor(path))
    }

    override suspend fun readWorkspace(reference: ProjectWorkspaceReference): ProjectWorkspaceDocument =
        withContext(Dispatchers.IO) {
            val path = validateWorkspacePath(Path.of(reference.sdkmanRcPath))
            val size = Files.size(path)
            require(size <= MAX_SDKMAN_RC_BYTES) {
                "The selected .sdkmanrc exceeds the ${MAX_SDKMAN_RC_BYTES / 1_024} KiB safety limit."
            }
            ProjectWorkspaceDocument(
                reference = referenceFor(path),
                projectDirectory = requireNotNull(path.parent).toString(),
                sdkmanRc = parseSdkmanRc(path.fileName.toString(), Files.readString(path)),
            )
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

private fun chooseSdkmanRcPath(): Path? {
    val selectedFile = onEdt {
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose a project .sdkmanrc"
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("SDKMAN project files (.sdkmanrc)", "sdkmanrc")
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    } ?: return null
    return validateWorkspacePath(selectedFile.toPath())
}

private fun referenceFor(path: Path): ProjectWorkspaceReference {
    val validated = validateWorkspacePath(path)
    val projectName = validated.parent?.fileName?.toString()
        ?.takeIf(String::isNotBlank)
        ?: "SDKMAN project"
    return ProjectWorkspaceReference(
        sdkmanRcPath = validated.toString(),
        displayName = projectName,
    )
}

internal fun validateWorkspacePath(input: Path): Path {
    val normalized = input.toAbsolutePath().normalize()
    require(normalized.fileName?.toString() == ".sdkmanrc") {
        "Choose a file named .sdkmanrc."
    }
    var current = requireNotNull(normalized.root)
    normalized.forEach { segment ->
        current = current.resolve(segment)
        require(!Files.isSymbolicLink(current)) {
            "Project workspace paths cannot contain symbolic links."
        }
    }
    require(Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
        "The selected .sdkmanrc is not a regular file."
    }
    return normalized.toRealPath(LinkOption.NOFOLLOW_LINKS)
}

private const val MAX_SDKMAN_RC_BYTES = 1_048_576L
