package app.renami.gui;

import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Registers a file/folder drag-and-drop target on a component.
 * A single dropped folder is passed through as-is; individual files are passed as a list.
 */
final class FileDrop {

    /** Receives either [folder] or [file, file, …] from a drop gesture. */
    static void register(java.awt.Component component, Consumer<List<Path>> handler) {
        new DropTarget(component, new java.awt.dnd.DropTargetAdapter() {
            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent event) {
                try {
                    event.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> dropped = (List<File>) event.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    handler.accept(dropped.stream().map(File::toPath).toList());
                    event.dropComplete(true);
                } catch (Exception e) {
                    event.dropComplete(false);
                }
            }
        });
    }

    private FileDrop() {
    }
}
