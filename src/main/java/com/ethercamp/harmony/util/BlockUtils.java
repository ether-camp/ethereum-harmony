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

import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

/**
 * Number of utils for {@link Block} related calculations
 */
public class BlockUtils {

    /**
     * Calculates average difficulty of blocks
     * Ignores first block if there are more than 2 blocks
     * @param blocks   input blocks
     * @param includeUncles if set to true, difficulty of its uncles is added to each block difficulty
     * @return average block difficulty
     */
    public static BigInteger calculateAvgDifficulty(List<Block> blocks, boolean includeUncles) {
        if (blocks.isEmpty()) {
            return BigInteger.ZERO;
        }

        if (blocks.size() == 1) {
            return blocks.get(0).getDifficultyBI();
        }

        // Calculating sum of difficulties for blocks [1, last]
        BigInteger sumDifficulties = BigInteger.ZERO;
        for (int i = 1; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            sumDifficulties = sumDifficulties.add(block.getDifficultyBI());
            if (includeUncles) {
                for (BlockHeader uncle : block.getUncleList()) {
                    sumDifficulties = sumDifficulties.add(uncle.getDifficultyBI());
                }
            }
        }

        return new BigDecimal(sumDifficulties)
                .divide(BigDecimal.valueOf(blocks.size() - 1), RoundingMode.FLOOR)
                .toBigInteger();
    }

    /**
     * Calculates blocks hash rate
     * Uses only blocks from 2nd to last. 1st block is used only to calculate 2nd block mining time.
     * @param blocks   input blocks
     * @return Average hash rate / second
     */
    public static BigInteger calculateHashRate(List<Block> blocks) {
        return calculateHashRate(blocks, calculateAvgDifficulty(blocks, true));
    }

    /**
     * Calculates block hash rate for blocks in lastBlocksForHashRate
     * Uses only blocks from 2nd to last. 1st block is used only to calculate 2nd block mining time.
     * @param blocks   input blocks
     * @param blockDifficulty   Average difficulty for blocks [1, last] of lastBlocksForHashRate
     * @return Average hash rate / second
     */
    private static BigInteger calculateHashRate(List<Block> blocks, BigInteger blockDifficulty) {
        if (blocks.size() < 2) {
            return BigInteger.ZERO;
        }

        final Block firstBlock = blocks.get(0);
        final Block bestBlock = blocks.get(blocks.size() - 1);
        // Average block time for blocks [1, last]
        float avgTime = ((float) (bestBlock.getTimestamp() - firstBlock.getTimestamp()) / (blocks.size() - 1));

        if (avgTime > 0) {
            return new BigDecimal(blockDifficulty)
                    .divide(new BigDecimal(avgTime), RoundingMode.FLOOR).toBigInteger(); // avg block difficulty / avg block seconds
        } else {
            return BigInteger.ZERO;
        }
    }
}
