/*
 * Copyright 2015, 2016 Ether.Camp Inc. (US)
 * This file is part of Ethereum Harmony.
 *
 * Ethereum Harmony is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Ethereum Harmony is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Ethereum Harmony.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ethercamp.harmony.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;

/**
 * @author Mikhail Kalinin
 * @since 23.05.2018
 */
@Slf4j(topic = "harmony")
public class ProcessUtils {

    public static void dumpOpenFiles() {
        try {
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            File dir = Paths.get(System.getProperty("user.dir"), "lsof").toFile();
            if (!dir.exists()) dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(Paths.get(dir.getAbsolutePath(),
                    "lsof_pid" + pid + "_" + System.currentTimeMillis() / 1000 + ".out").toFile())) {

                byte[] buffer = new byte[1 << 10];
                Process proc = Runtime.getRuntime().exec(new String[]{"lsof", "-p", pid});
                InputStream in = proc.getInputStream();
                while (in.read(buffer) > 0)
                    out.write(buffer);
            }
        } catch (Throwable t) {
            log.error("Failed to dump open files", t);
        }
    }
}
