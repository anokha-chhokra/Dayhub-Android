package app.dayhub;

import app.dayhub.data.BackupSink;

import java.io.IOException;

/** Connects the backup logic to the real backup file chosen through Android's file picker. */
public final class BackupFileSink implements BackupSink {
    private final BackupFile file;

    public BackupFileSink(BackupFile file) {
        this.file = file;
    }

    @Override
    public boolean configured() {
        return file.configured();
    }

    @Override
    public boolean auto() {
        return file.prefs().auto();
    }

    @Override
    public long fileBytes() {
        return file.prefs().bytes();
    }

    @Override
    public void write(String text) throws IOException {
        file.write(text);
    }
}
