package com.ethercamp.harmony.web.filter;

import com.ethercamp.harmony.service.JsonRpcUsageService;
import com.ethercamp.harmony.util.AppConst;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.*;

/**
 * Intercepts JSON-RPC requests and updates usage stats.
 *
 * Created by Stan Reshetnyk on 22.07.16.
 */
@Slf4j(topic = "jsonrpc")
@WebFilter(urlPatterns = AppConst.JSON_RPC_PATH)
public class JsonRpcUsageFilter implements Filter {

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
                ObjectMapper mapper = new ObjectMapper();
                mapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true);

                try {
                    final ResettableStreamHttpServletRequest wrappedRequest = new ResettableStreamHttpServletRequest(
                            (HttpServletRequest) request);
                    final String body = IOUtils.toString(wrappedRequest.getReader());

                    wrappedRequest.resetInputStream();

                    final JsonNode json = mapper.readTree(body);
                    final String methodName = json.get("method").asText();

                    if (response.getCharacterEncoding() == null) {
                        response.setCharacterEncoding("UTF-8");
                    }
                    HttpServletResponseCopier responseCopier = new HttpServletResponseCopier((HttpServletResponse) response);

                    try {
                        chain.doFilter(wrappedRequest, responseCopier);
                        responseCopier.flushBuffer();
                    } finally {
                        final byte[] copy = responseCopier.getCopy();
                        final String responseText = new String(copy, response.getCharacterEncoding());
                        jsonRpcUsageService.updateStats(methodName, responseText);
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
                servletStream.stream = new ByteArrayInputStream(rawData);
            }
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


