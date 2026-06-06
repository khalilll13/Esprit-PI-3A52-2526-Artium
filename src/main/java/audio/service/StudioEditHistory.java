package audio.service;

import audio.model.AudioBuffer;
import audio.model.StudioProject;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo/redo stack storing project buffer snapshots.
 */
public final class StudioEditHistory {
    private static final int MAX = 40;
    private final Deque<AudioBuffer> undo = new ArrayDeque<>();
    private final Deque<AudioBuffer> redo = new ArrayDeque<>();

    public void push(StudioProject project) {
        AudioBuffer snap = project.getMasterBuffer().clone();
        undo.push(snap);
        if (undo.size() > MAX) {
            undo.removeLast();
        }
        redo.clear();
    }

    public boolean undo(StudioProject project) {
        if (undo.isEmpty()) {
            return false;
        }
        redo.push(project.getMasterBuffer().clone());
        project.setMasterBuffer(undo.pop());
        return true;
    }

    public boolean redo(StudioProject project) {
        if (redo.isEmpty()) {
            return false;
        }
        undo.push(project.getMasterBuffer().clone());
        project.setMasterBuffer(redo.pop());
        return true;
    }

    public void clear() {
        undo.clear();
        redo.clear();
    }
}
