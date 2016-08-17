package com.ethercamp.harmony.web.filter;

import com.ethercamp.harmony.service.JsonRpcUsageService;
import com.ethercamp.harmony.util.AppConst;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;

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
public class JsonRpcUsageFilter implements Filter {

    private static final List<String> EXCLUDE_LOGS = Arrays.asList("eth_getLogs", "eth_getFilterLogs",
            "personal_newAccount", "personal_importRawKey", "personal_unlockAccount");
    @Autowired
    JsonRpcUsageService jsonRpcUsageService;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if ((request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;

            if (AppConst.JSON_RPC_PATH.equals(httpRequest.getRequestURI())) {
                final ObjectMapper mapper = new ObjectMapper();

                try {
                    final ResettableStreamHttpServletRequest wrappedRequest = new ResettableStreamHttpServletRequest(
                            (HttpServletRequest) request);

                    final String body = IOUtils.toString(wrappedRequest.getReader());

                    // read request for log later
                    final JsonNode json = mapper.readTree(body);
                    final String methodName = json.get("method").asText();
                    final List<String> params = new ArrayList<>();
                    json.get("params").forEach(n -> params.add(n.asText()));

                    wrappedRequest.resetInputStream();

                    if (response.getCharacterEncoding() == null) {
                        response.setCharacterEncoding("UTF-8");
                    }
                    HttpServletResponseCopier responseCopier = new HttpServletResponseCopier((HttpServletResponse) response);

                    try {
                        chain.doFilter(wrappedRequest, responseCopier);
                        responseCopier.flushBuffer();
                    } finally {
                        // read response for stats and log
                        final byte[] copy = responseCopier.getCopy();
                        final String responseText = new String(copy, response.getCharacterEncoding());
                        jsonRpcUsageService.methodInvoked(methodName, responseText);

                        if (log.isDebugEnabled()) {
                            // passwords could logged here
                            if (!EXCLUDE_LOGS.contains(methodName)) {
                                log.debug(methodName + "(" + params.stream().collect(Collectors.joining(", ")) + "): " + responseText);
                            } else {
                                // logging is handled manually in service
                            }
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


        private class ResettableServletInputStream extends ServletInputStream {

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

    public class ServletOutputStreamCopier extends ServletOutputStream {

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

    public class HttpServletResponseCopier extends HttpServletResponseWrapper {

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


