package su.knst.crypto.utils.sys;

import su.knst.crypto.Main;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class SystemCommandsBridge {
    public static void runSystemCommand(String[] args) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(args);
        builder.redirectErrorStream(true);
        builder.directory(Main.getCurrentPath().toFile());

        Process p = builder.start();
        BufferedReader processReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));

        String line;

        while (true) {
            while ((line = processReader.readLine()) != null)
                System.out.println(line);

            try {
                p.waitFor(1, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {

            }

            if (!p.isAlive())
                break;

            System.out.print("#cu/console> ");

            String command = reader.readLine();
            writer.write(command);
        }
    }

}
