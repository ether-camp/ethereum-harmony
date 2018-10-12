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

import static com.ethercamp.harmony.util.AppConst.JSON_RPC_ALIAS_PATH;
import static com.ethercamp.harmony.util.AppConst.JSON_RPC_PATH;

/**
 * Filters web and rpc requests to ensure that
 * they are performed to the right port
 */
@Slf4j
@WebFilter()
public class ModulePortFilter implements Filter {
    private Integer rpcPort;
    private Integer webPort;

    public ModulePortFilter(Integer rpcPort, Integer webPort) {
        this.rpcPort = rpcPort;
        this.webPort = webPort;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if(request instanceof HttpServletRequest) {
            if (isRpcRequest((HttpServletRequest) request)) { // RPC request
                if (isRequestToWebPort(request)) {
                    ((HttpServletResponse) response).sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            } else { // Not RPC request
                if (isRequestToRpcPort(request)) {
                    ((HttpServletResponse) response).sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isRpcRequest(HttpServletRequest request) {
        return request.getRequestURI().equals(JSON_RPC_PATH) ||
                ("POST".equals(request.getMethod()) && request.getRequestURI().equals(JSON_RPC_ALIAS_PATH));
    }

    private boolean isRequestToRpcPort(ServletRequest request) {
        return rpcPort != null && request.getLocalPort() == rpcPort;
    }

    private boolean isRequestToWebPort(ServletRequest request) {
        return webPort != null && request.getLocalPort() == webPort;
    }

    @Override
    public void destroy() {

    }

    public static final Filter DUMMY = new Filter() {
        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
        }
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            chain.doFilter(request, response);
        }
        @Override
        public void destroy() {
        }
    };
}
