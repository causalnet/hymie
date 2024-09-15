package au.net.causal.hymie.filter;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.ProtocolException;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class GzipContentFilter implements ContentFilter
{
    @Override
    public InputStream applyFilter(InputStream content, MessageHeaders headers) throws IOException
    {
        try
        {
            Header encoding = headers.getHeader(HttpHeaders.CONTENT_ENCODING);
            if (encoding != null && "gzip".equalsIgnoreCase(encoding.getValue()))
                return new GZIPInputStream(content);
        }
        catch (ProtocolException e)
        {
            //Could not read headers, pass through
        }

        //Leave original stream alone
        return content;
    }
}
