package au.net.causal.hymie;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.impl.io.ChunkedInputStream;
import org.apache.hc.core5.http.impl.io.ContentLengthInputStream;
import org.apache.hc.core5.http.impl.io.DefaultHttpRequestParser;
import org.apache.hc.core5.http.impl.io.DefaultHttpResponseParser;
import org.apache.hc.core5.http.impl.io.IdentityInputStream;
import org.apache.hc.core5.http.impl.io.SessionInputBufferImpl;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.http.io.entity.BufferedHttpEntity;
import org.apache.hc.core5.http.io.entity.EmptyInputStream;
import org.apache.hc.core5.http.message.BasicLineParser;
import org.apache.hc.core5.io.Closer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class HttpExchangeParser
{
    private final ContentLengthStrategy contentLengthStrategy = new DefaultContentLengthStrategy();
    private final Http1Config http1Config = Http1Config.DEFAULT;

    public Exchange parse(SocketAddress address, Instant fromTime, Instant toTime, byte[] rawRequest, byte[] rawResponse)
    throws IOException, HttpException
    {
        DefaultHttpRequestParser requestParser = new DefaultHttpRequestParser();
        DefaultHttpResponseParser responseParser = new DefaultHttpResponseParser(new BasicLineParser(), null, http1Config);

        //Parse request
        SessionInputBufferImpl requestBuf = new SessionInputBufferImpl(http1Config.getBufferSize());
        ClassicHttpRequest request;
        try (InputStream is = new ByteArrayInputStream(rawRequest))
        {
            request = requestParser.parse(requestBuf, is);
            receiveRequestEntity(request, requestBuf, is);
        }

        //Parse response
        SessionInputBufferImpl responseBuf = new SessionInputBufferImpl(http1Config.getBufferSize());
        ClassicHttpResponse response;
        try (InputStream is = new ByteArrayInputStream(rawResponse))
        {
            response = responseParser.parse(responseBuf, is);
            receiveResponseEntity(response, responseBuf, is);
        }

        return new Exchange(address, fromTime, toTime, request, response);
    }

    private void receiveRequestEntity(final ClassicHttpRequest request, SessionInputBuffer inBuffer, InputStream is)
    throws HttpException, IOException
    {
        final long len = contentLengthStrategy.determineLength(request);
        request.setEntity(new BufferedHttpEntity(createIncomingEntity(request, inBuffer, is, len, http1Config)));
    }

    private void receiveResponseEntity(final ClassicHttpResponse response, SessionInputBuffer inBuffer, InputStream is)
    throws HttpException, IOException
    {
        final long len = contentLengthStrategy.determineLength(response);
        response.setEntity(new BufferedHttpEntity(createIncomingEntity(response, inBuffer, is, len, http1Config)));
    }

    HttpEntity createIncomingEntity(
            final HttpMessage message,
            final SessionInputBuffer inBuffer,
            final InputStream inputStream,
            final long len, Http1Config http1Config)
    {
        return new IncomingHttpEntity(
                createContentInputStream(len, inBuffer, inputStream, http1Config),
                len >= 0 ? len : -1, len == ContentLengthStrategy.CHUNKED,
                message.getFirstHeader(HttpHeaders.CONTENT_TYPE),
                message.getFirstHeader(HttpHeaders.CONTENT_ENCODING));
    }

    private InputStream createContentInputStream(
            final long len,
            final SessionInputBuffer buffer,
            final InputStream inputStream,
            Http1Config http1Config)
    {
        if (len > 0) {
            return new ContentLengthInputStream(buffer, inputStream, len);
        } else if (len == 0) {
            return EmptyInputStream.INSTANCE;
        } else if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkedInputStream(buffer, inputStream, http1Config);
        } else {
            return new IdentityInputStream(buffer, inputStream);
        }
    }


    static class IncomingHttpEntity implements HttpEntity
    {
        private final InputStream content;
        private final long len;
        private final boolean chunked;
        private final Header contentType;
        private final Header contentEncoding;

        IncomingHttpEntity(final InputStream content, final long len, final boolean chunked, final Header contentType, final Header contentEncoding)
        {
            this.content = content;
            this.len = len;
            this.chunked = chunked;
            this.contentType = contentType;
            this.contentEncoding = contentEncoding;
        }

        @Override
        public boolean isRepeatable()
        {
            return false;
        }

        @Override
        public boolean isChunked()
        {
            return chunked;
        }

        @Override
        public long getContentLength()
        {
            return len;
        }

        @Override
        public String getContentType()
        {
            return contentType != null ? contentType.getValue() : null;
        }

        @Override
        public String getContentEncoding()
        {
            return contentEncoding != null ? contentEncoding.getValue() : null;
        }

        @Override
        public InputStream getContent() throws IOException, IllegalStateException
        {
            return content;
        }

        @Override
        public boolean isStreaming()
        {
            return content != null && content != EmptyInputStream.INSTANCE;
        }

        @Override
        public void writeTo(final OutputStream outStream) throws IOException
        {
            AbstractHttpEntity.writeTo(this, outStream);
        }

        @Override
        public Supplier<List<? extends Header>> getTrailers()
        {
            return null;
        }

        @Override
        public Set<String> getTrailerNames()
        {
            return Collections.emptySet();
        }

        @Override
        public void close() throws IOException
        {
            Closer.close(content);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append('[');
            sb.append("Content-Type: ");
            sb.append(getContentType());
            sb.append(',');
            sb.append("Content-Encoding: ");
            sb.append(getContentEncoding());
            sb.append(',');
            final long len = getContentLength();
            if (len >= 0) {
                sb.append("Content-Length: ");
                sb.append(len);
                sb.append(',');
            }
            sb.append("Chunked: ");
            sb.append(isChunked());
            sb.append(']');
            return sb.toString();
        }
    }

    public static class Exchange
    {
        private final SocketAddress address;
        private final Instant fromTime;
        private final Instant toTime;
        private final ClassicHttpRequest request;
        private final ClassicHttpResponse response;

        public Exchange(SocketAddress address, Instant fromTime, Instant toTime, ClassicHttpRequest request, ClassicHttpResponse response)
        {
            this.address = address;
            this.fromTime = fromTime;
            this.toTime = toTime;
            this.request = request;
            this.response = response;
        }

        public SocketAddress getAddress()
        {
            return address;
        }

        public Instant getFromTime()
        {
            return fromTime;
        }

        public Instant getToTime()
        {
            return toTime;
        }

        public ClassicHttpRequest getRequest()
        {
            return request;
        }

        public ClassicHttpResponse getResponse()
        {
            return response;
        }
    }
}
