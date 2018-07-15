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

package com.ethercamp.harmony.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

import static com.ethercamp.harmony.util.AppConst.JSON_RPC_ALIAS_PATH;
import static com.ethercamp.harmony.util.AppConst.JSON_RPC_PATH;

/**
 * TODO: Add some info
 */
@Slf4j
@WebFilter()
@Conditional(RpcEnabledCondition.class)
public class ModulePortFilter implements Filter {
    private Integer rpcPort;
    private Integer webPort;
    private boolean samePort = false;

    public ModulePortFilter(Integer rpcPort, Integer webPort) {
        this.rpcPort = rpcPort;
        this.webPort = webPort;
        if (Objects.equals(rpcPort, webPort)) {
            samePort = true;
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if(request instanceof HttpServletRequest && !samePort) {
            if ((((HttpServletRequest) request).getRequestURI().equals(JSON_RPC_PATH)) ||
                    ("POST".equals(((HttpServletRequest) request).getMethod()) &&
                            ((HttpServletRequest) request).getRequestURI().equals(JSON_RPC_ALIAS_PATH))) { // RPC request
                if (webPort != null && request.getLocalPort() == webPort) {
                    ((HttpServletResponse) response).sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            } else { // Other requests
                if (rpcPort != null && request.getLocalPort() == rpcPort) {
                    ((HttpServletResponse) response).sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {

    }
}
