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

package com.ethercamp.harmony.keystore;

import org.ethereum.crypto.ECKey;

/**
 * Created by Stan Reshetnyk on 01.08.16.
 *
 * Each method could throw {RuntimeException}, because of access to IO and crypto functions.
 */
public interface Keystore {

    void removeKey(String address);

    void storeKey(ECKey key, String password) throws RuntimeException;

    void storeRawKeystore(String content, String address) throws RuntimeException;

    String[] listStoredKeys();

    ECKey loadStoredKey(String address, String password) throws RuntimeException;

    /**
     * Check if keystore has file with key for passed address.
     * @param address - 40 chars
     * @return
     */
    boolean hasStoredKey(String address);
}
