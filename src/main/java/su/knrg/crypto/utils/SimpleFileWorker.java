package su.knrg.crypto.utils;

import su.knrg.crypto.utils.exceptions.CantReadFromTargetException;
import su.knrg.crypto.utils.exceptions.CantWriteToTargetException;
import su.knrg.crypto.utils.exceptions.TargetNotFileException;

import java.io.*;

public class SimpleFileWorker {
    protected final File target;

    public static SimpleFileWorker of(File target) throws IOException {
        return new SimpleFileWorker(target);
    }

    public static SimpleFileWorker of(String path) throws IOException {
        return of(new File(path));
    }

    public SimpleFileWorker(File target) throws TargetNotFileException, IOException {
        if (!target.exists()) {
            File parent = target.getParentFile();
            if (parent != null)
                target.getParentFile().mkdirs();

            target.createNewFile();
        }

        if (!target.isFile())
            throw new TargetNotFileException();

        this.target = target;
    }

    public void writeToFile(byte[] bytes) throws IOException {
        if (!target.canWrite())
            throw new CantWriteToTargetException();

        BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(target));

        stream.write(bytes);
        stream.flush();
        stream.close();
    }

    public void writeToFile(String text) throws IOException {
        writeToFile(text.getBytes());
    }

    public byte[] readBytesFromFile() throws IOException {
        if (!target.canRead())
            throw new CantReadFromTargetException();

        BufferedInputStream stream = new BufferedInputStream(new FileInputStream(target));
        byte[] result = stream.readAllBytes();

        stream.close();

        return result;
    }

    public String readFromFile() throws IOException {
        return new String(readBytesFromFile());
    }
}
