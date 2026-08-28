package net.morsecode.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.WindowScope
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import net.morsecode.shared.platform.PickedFile
import net.morsecode.shared.ui.AppViewModel

/**
 * Native drag-and-drop (Section D): dropping files onto the window queues
 * them for sending via the Home > Send flow.
 */
@Composable
fun WindowScope.DragDropHost(vm: AppViewModel) {
    DisposableEffect(Unit) {
        val adapter = object : DropTargetAdapter() {
            override fun dragEnter(event: DropTargetDragEvent) {
                event.acceptDrag(DnDConstants.ACTION_COPY)
            }

            override fun dragOver(event: DropTargetDragEvent) {
                event.acceptDrag(DnDConstants.ACTION_COPY)
            }

            override fun drop(event: DropTargetDropEvent) {
                event.acceptDrop(DnDConstants.ACTION_COPY)
                val files = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<java.io.File>
                }.getOrDefault(emptyList())
                event.dropComplete(files.isNotEmpty())
                if (files.isNotEmpty()) {
                    vm.pendingSendFiles.value = files.map { f ->
                        PickedFile(
                            uri = f.absolutePath,
                            displayName = f.name,
                            sizeBytes = f.length(),
                            mime = net.morsecode.shared.platform.guessMime(f.name),
                        )
                    }
                    vm.toast("${files.size} file(s) added - open Home > Send to choose recipients")
                }
            }
        }
        val target = DropTarget(window, adapter)
        onDispose { target.removeDropTargetListener(adapter) }
    }
}
