package com.ethercamp.harmony.service.contracts.compiler;

import lombok.extern.slf4j.Slf4j;
import org.ethereum.config.SystemProperties;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Created by Anton Nashatyrev on 03.03.2016.
 */
@Slf4j(topic = "contracts")
public class Solc {

    private File solc = null;

    Solc(SystemProperties config) {
        try {
            init(config);
        } catch (IOException e) {
            throw new RuntimeException("Can't init solc compiler: ", e);
        }
    }

    private void init(SystemProperties config) throws IOException {
        initBundled();
    }

    private void initBundled() throws IOException {
        File tmpDir = new File(System.getProperty("java.io.tmpdir"), "solc");
        tmpDir.mkdirs();

        // files were extracted here from jar dependency in gradle script
        String location = "solcJ-all/native/" + getOS() + "/solc/";

        final InputStream is = getResource(location + "file.list");
        final Scanner scanner = new Scanner(is);
        while (scanner.hasNext()) {
            String s = scanner.next();
            File targetFile = new File(tmpDir, s);
            if (!targetFile.canRead()) {
                InputStream fis = getResource(location + s);
                Files.copy(fis, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            if (solc == null) {
                // first file in the list denotes executable
                solc = targetFile;
                solc.setExecutable(true);
            }
            targetFile.deleteOnExit();
        }
        log.info("Solc initialized");
        String solcVersionOutput = execCmd(getExecutable().getAbsolutePath() + " --version");
        Arrays.asList(solcVersionOutput.split("\\n"))
                .forEach(line -> log.info(line));

    }

    public static String execCmd(String cmd) throws java.io.IOException {
        java.util.Scanner s = new java.util.Scanner(Runtime.getRuntime().exec(cmd).getInputStream()).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    private InputStream getResource(String resourceName) throws FileNotFoundException {
        return getClass().getClassLoader().getResourceAsStream(resourceName);
    }

    private String getOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return "win";
        } else if (osName.contains("linux")) {
            return "linux";
        } else if (osName.contains("mac")) {
            return "mac";
        } else {
            throw new RuntimeException("Can't find solc compiler: unrecognized OS: " + osName);
        }
    }

    public File getExecutable() {
        return solc;
    }
}
