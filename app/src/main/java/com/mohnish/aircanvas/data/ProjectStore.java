package com.mohnish.aircanvas.data;

import android.content.Context;
import android.util.AtomicFile;

import com.mohnish.aircanvas.model.DesignDocument;

import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ProjectStore {
    private static final String AUTOSAVE = "_autosave.aircanvas.json";
    private final File directory;

    public ProjectStore(Context context) {
        directory = new File(context.getFilesDir(), "projects");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Unable to create projects directory");
        }
    }

    public synchronized void save(DesignDocument document) throws IOException {
        document.touch();
        writeAtomically(fileFor(document.id), DocumentCodec.encode(document));
    }

    public synchronized void autosave(DesignDocument document) throws IOException {
        document.touch();
        writeAtomically(new File(directory, AUTOSAVE), DocumentCodec.encode(document));
    }

    public synchronized DesignDocument load(String id) throws IOException, JSONException {
        return DocumentCodec.decode(readText(fileFor(id)));
    }

    public synchronized DesignDocument loadAutosave() throws IOException, JSONException {
        File autosave = new File(directory, AUTOSAVE);
        return autosave.isFile() ? DocumentCodec.decode(readText(autosave)) : null;
    }

    public synchronized List<ProjectInfo> list() {
        List<ProjectInfo> result = new ArrayList<>();
        File[] files = directory.listFiles((dir, name) ->
                name.endsWith(".aircanvas.json") && !AUTOSAVE.equals(name)
        );
        if (files == null) {
            return result;
        }
        for (File file : files) {
            try {
                DesignDocument document = DocumentCodec.decode(readText(file));
                result.add(new ProjectInfo(
                        document.id,
                        document.name,
                        document.template,
                        document.updatedAt,
                        document.elements.size()
                ));
            } catch (IOException | JSONException ignored) {
                // A damaged project must not prevent healthy projects from opening.
            }
        }
        result.sort(Comparator.comparingLong(ProjectInfo::updatedAt).reversed());
        return result;
    }

    public synchronized boolean delete(String id) {
        File file = fileFor(id);
        return !file.exists() || file.delete();
    }

    private File fileFor(String id) {
        String safe = id == null ? "untitled" : id.replaceAll("[^a-zA-Z0-9_-]", "_");
        return new File(directory, safe + ".aircanvas.json");
    }

    private static void writeAtomically(File target, String value) throws IOException {
        AtomicFile atomicFile = new AtomicFile(target);
        FileOutputStream stream = null;
        try {
            stream = atomicFile.startWrite();
            stream.write(value.getBytes(StandardCharsets.UTF_8));
            stream.flush();
            stream.getFD().sync();
            atomicFile.finishWrite(stream);
        } catch (IOException | RuntimeException exception) {
            if (stream != null) {
                atomicFile.failWrite(stream);
            }
            throw exception;
        }
    }

    private static String readText(File file) throws IOException {
        if (!file.isFile()) {
            throw new IOException("Project not found");
        }
        if (file.length() > 20L * 1024L * 1024L) {
            throw new IOException("Project file is too large");
        }
        byte[] bytes = new byte[(int) file.length()];
        int offset = 0;
        try (FileInputStream stream = new FileInputStream(file)) {
            while (offset < bytes.length) {
                int read = stream.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
        }
        if (offset != bytes.length) {
            throw new IOException("Project file ended unexpectedly");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public record ProjectInfo(
            String id,
            String name,
            String template,
            long updatedAt,
            int elementCount
    ) {
    }
}
