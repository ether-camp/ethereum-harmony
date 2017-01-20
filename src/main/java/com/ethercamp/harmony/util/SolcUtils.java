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

import org.ethereum.solidity.compiler.SolidityCompiler;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Stan Reshetnyk on 20.01.17.
 */
public class SolcUtils {

    public static String getSolcVersion() {
        try {
            // optimistic parsing of version string
            final String versionOutput = SolidityCompiler.runGetVersionOutput();
            final Matcher matcher = Pattern.compile("(\\d+.\\d+.\\d+)").matcher(versionOutput);
            matcher.find();
            return matcher.group(0);
        } catch (Exception e) {
            LoggerFactory.getLogger("general").error("Problem reading solidity version", e);
            return null;
        }
    }
}
