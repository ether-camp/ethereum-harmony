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

package com.ethercamp.harmony;

import com.ethercamp.harmony.config.EthereumHarmonyConfig;
import org.ethereum.config.SystemProperties;
import org.ethereum.manager.BlockLoader;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.math.NumberUtils.toInt;
import static org.ethereum.facade.EthereumFactory.createEthereum;

@SpringBootApplication(exclude = HibernateJpaAutoConfiguration.class)
@EnableScheduling
@Import({EthereumHarmonyConfig.class})
public class Application {

    /**
     * Does one of:
     * - start Harmony peer;
     * - perform action and exit on completion.
     */
    public static void main(String[] args) throws Exception {
        SystemProperties config = SystemProperties.getDefault();

        getBlocksDumpPath(config).ifPresent(dumpPath -> loadDumpAndExit(config, dumpPath));

        // Overriding mine.start to get control of its startup
        // in {@link com.ethercamp.harmony.service.PrivateMinerService}
        config.overrideParams("mine.start", "false");
        SpringApplication.run(Application.class, args);
    }

    private static Optional<Path> getBlocksDumpPath(SystemProperties config) {
        String blocksLoader = config.blocksLoader();

        if (isEmpty(blocksLoader)) {
            return Optional.empty();
        } else {
            Path path = Paths.get(blocksLoader);
            return Files.exists(path) ? Optional.of(path) : Optional.empty();
        }
    }

    private static Optional<Function<Path, BlockLoader.DumpWalker>> getDumpWalkerFactory(SystemProperties config) {
        return "rlp".equals(config.getProperty("blocks.format", EMPTY))
                ? Optional.of(RlpDumpWalker::new)
                : Optional.empty();
    }

    /**
     * Loads single or multiple block dumps from specified path, and terminate program execution.<br>
     * Exit code is 0 in case of successfully dumps loading, 1 otherwise.
     *
     * @param config {@link SystemProperties} config instance;
     * @param path   file system path to dump file or directory that contains dumps;
     */
    private static void loadDumpAndExit(SystemProperties config, Path path) {
        config.setSyncEnabled(false);
        config.setDiscoveryEnabled(false);

        boolean loaded = false;
        try {
            Optional<Function<Path, BlockLoader.DumpWalker>> factory = getDumpWalkerFactory(config);
            Path[] paths;
            if (Files.isDirectory(path)) {
                Pattern pattern = Pattern.compile("(\\D+)?(\\d+)?(.*)?");

                paths = Files.list(path)
                        .sorted(Comparator.comparingInt(filePath -> {
                            String fileName = filePath.getFileName().toString();
                            Matcher matcher = pattern.matcher(fileName);
                            return matcher.matches() ? toInt(matcher.group(2)) : 0;
                        }))
                        .toArray(Path[]::new);
            } else {
                paths = new Path[]{path};
            }

            BlockLoader blockLoader = createEthereum().getBlockLoader();
            loaded = factory.isPresent()
                    ? blockLoader.loadBlocks(factory.get(), paths)
                    : blockLoader.loadBlocks(paths);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.exit(loaded ? 0 : 1);
    }

    public static class RlpDumpWalker implements BlockLoader.DumpWalker {

        private Iterator<RLPElement> iterator;

        public RlpDumpWalker(Path path) {
            try {
                System.out.println("Loading rlp encoded blocks dump from: " + path);
                // NOT OPTIMAL, but fine for tests
                byte[] data = Files.readAllBytes(path);
                this.iterator = RLP.decode2(data, 1).iterator();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Iterator<byte[]> iterator() {
            return new Iterator<byte[]>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public byte[] next() {
                    return iterator.next().getRLPData();
                }
            };
        }
    }
}
