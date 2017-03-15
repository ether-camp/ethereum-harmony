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

package com.ethercamp.harmony.desktop;

import org.springframework.beans.BeansException;
import org.springframework.boot.context.embedded.EmbeddedServletContainerException;
import org.springframework.context.ApplicationContextException;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Stan Reshetnyk on 07.03.17.
 */
public class DesktopUtil {

    /**
     * Find initial exception by visiting
     */
    public static Throwable findCauseFromSpringException(Throwable e) {
        final List<Class<? extends Exception>> skipList = Arrays.asList(BeansException.class, EmbeddedServletContainerException.class, ApplicationContextException.class);

        for (int i = 0; i < 50; i++) {
            final Throwable inner = e;
            final boolean isSkipped = skipList.stream().anyMatch(c -> c.isAssignableFrom(inner.getClass()));
            if (isSkipped) {
                e = e.getCause();
            } else {
                return e;
            }
        }
        return e;
    }
}
