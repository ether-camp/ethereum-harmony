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

package com.ethercamp.harmony.web.filter;

import com.ethercamp.harmony.config.RpcEnabledCondition;
import com.ethercamp.harmony.service.JsonRpcUsageService;
import com.ethercamp.harmony.util.AppConst;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Intercept JSON-RPC requests and updates usage stats.
 *  - use custom request wrapper to allow read input stream multiple times;
 *  - use custom response wrapper to allow read output stream multiple times.
 *
 * Created by Stan Reshetnyk on 22.07.16.
 */
@Slf4j(topic = "jsonrpc")
@WebFilter(urlPatterns = AppConst.JSON_RPC_PATH)
@Conditional(RpcEnabledCondition.class)
public class JsonRpcUsageFilter implements Filter {

    private static final List<String> EXCLUDE_LOGS = Arrays.asList("eth_getLogs", "eth_getFilterLogs",
            "personal_newAccount", "personal_importRawKey", "personal_unlockAccount", "personal_signAndSendTransaction");

    @Autowired
    JsonRpcUsageService jsonRpcUsageService;

    final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if ((request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            final HttpServletRequest httpRequest = (HttpServletRequest) request;
            final HttpServletResponse httpResponse = (HttpServletResponse) response;

            // don't count alias as it redirects here
            final boolean isJsonRpcUrl = AppConst.JSON_RPC_PATH.equals(httpRequest.getRequestURI());

            if (isJsonRpcUrl && httpRequest.getMethod().equalsIgnoreCase("POST")) {

                try {
                    final ResettableStreamHttpServletRequest wrappedRequest = new ResettableStreamHttpServletRequest(
                            httpRequest);

                    final String body = IOUtils.toString(wrappedRequest.getReader());

                    wrappedRequest.resetInputStream();

                    if (response.getCharacterEncoding() == null) {
                        response.setCharacterEncoding("UTF-8");
                    }
                    final HttpServletResponseCopier responseCopier = new HttpServletResponseCopier(httpResponse);

                    try {
                        chain.doFilter(wrappedRequest, responseCopier);
                        responseCopier.flushBuffer();
                    } finally {
                        // read response for stats and log
                        final byte[] copy = responseCopier.getCopy();
                        final String responseText = new String(copy, response.getCharacterEncoding());

                        final JsonNode json = mapper.readTree(body);
                        final JsonNode responseJson = mapper.readTree(responseText);

                        if (json.isArray()) {
                            for (int i = 0; i < json.size(); i++) {
                                notifyInvocation(json.get(i), responseJson.get(i));
                            }
                        } else {
                            notifyInvocation(json, responseJson);
                        }

                        // According to spec, JSON-RPC 2 should return status 200 in case of error
                        if (httpResponse.getStatus() == 500) {
                            httpResponse.setStatus(200);
                        }
                    }

                } catch (IOException e) {
                    log.error("Error parsing JSON-RPC request", e);
                }
            } else {
                chain.doFilter(request, response);
            }
        } else {
            throw new RuntimeException("JsonRpcUsageFilter supports only HTTP requests.");
        }
    }

    private void notifyInvocation(JsonNode requestJson, JsonNode responseJson) throws IOException {
        if (responseJson.has("error")) {
            final String errorMessage = responseJson.get("error").toString();
            log.warn("Problem when invoking JSON-RPC " + requestJson.toString() + " response:" + errorMessage);
        } else {
            final String methodName = requestJson.get("method").asText();
            final List<JsonNode> params = new ArrayList<>();
            if (requestJson.has("params")) {
                requestJson.get("params")
                        .forEach(n -> params.add(n));
            }

            final String responseText = mapper.writeValueAsString(responseJson);
            jsonRpcUsageService.methodInvoked(methodName, responseText);

            if (log.isInfoEnabled()) {
                // passwords could be sent here
                if (!EXCLUDE_LOGS.contains(methodName)) {
                    log.info(methodName + "(" + params.stream()
                            .map(n -> n.asText())
                            .collect(Collectors.joining(", ")) + "): " + responseText);
                } else {
                    // logging is handled manually in service
                }
            }
        }
    }

    private String dumpToString(JsonNode n) {
        if (n.isTextual()) {
            return n.asText();
        } else {
            try {
                return mapper.writeValueAsString(n);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }


    @Override
    public void destroy() {

    }


    private static class ResettableStreamHttpServletRequest extends
            HttpServletRequestWrapper {

        private byte[] rawData;
        private HttpServletRequest request;
        private ResettableServletInputStream servletStream;

        public ResettableStreamHttpServletRequest(HttpServletRequest request) {
            super(request);
            this.request = request;
            this.servletStream = new ResettableServletInputStream();
        }


        public void resetInputStream() {
            servletStream.stream = new ByteArrayInputStream(rawData);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (rawData == null) {
                rawData = IOUtils.toByteArray(this.request.getReader());
                servletStream.stream = new ByteArrayInputStream(rawData);
            }
            return servletStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (rawData == null) {
                rawData = IOUtils.toByteArray(this.request.getReader());
            }
            servletStream = new ResettableServletInputStream();
            servletStream.stream = new ByteArrayInputStream(rawData);
            return new BufferedReader(new InputStreamReader(servletStream));
        }


        private static class ResettableServletInputStream extends ServletInputStream {

            private InputStream stream;

            @Override
            public int read() throws IOException {
                return stream.read();
            }

            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {

            }
        }
    }

    public static class ServletOutputStreamCopier extends ServletOutputStream {

        private OutputStream outputStream;
        private ByteArrayOutputStream copy;

        public ServletOutputStreamCopier(OutputStream outputStream) {
            this.outputStream = outputStream;
            this.copy = new ByteArrayOutputStream(1024);
        }

        @Override
        public void write(int b) throws IOException {
            outputStream.write(b);
            copy.write(b);
        }

        public byte[] getCopy() {
            return copy.toByteArray();
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener listener) {

        }
    }

    public static class HttpServletResponseCopier extends HttpServletResponseWrapper {

        private ServletOutputStream outputStream;
        private PrintWriter writer;
        private ServletOutputStreamCopier copier;

        public HttpServletResponseCopier(HttpServletResponse response) throws IOException {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (writer != null) {
                throw new IllegalStateException("getWriter() has already been called on this response.");
            }

            if (outputStream == null) {
                outputStream = getResponse().getOutputStream();
                copier = new ServletOutputStreamCopier(outputStream);
            }

            return copier;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (outputStream != null) {
                throw new IllegalStateException("getOutputStream() has already been called on this response.");
            }

            if (writer == null) {
                copier = new ServletOutputStreamCopier(getResponse().getOutputStream());
                writer = new PrintWriter(new OutputStreamWriter(copier, getResponse().getCharacterEncoding()), true);
            }

            return writer;
        }

        @Override
        public void flushBuffer() throws IOException {
            if (writer != null) {
                writer.flush();
            } else if (outputStream != null) {
                copier.flush();
            }
        }

        public byte[] getCopy() {
            if (copier != null) {
                return copier.getCopy();
            } else {
                return new byte[0];
            }
        }

    }
}


