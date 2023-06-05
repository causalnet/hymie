package au.net.causal.hymie;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

public class PlainTrafficReporter implements TrafficReporter
{
    @Override
    public void report(Collection<? extends HttpExchangeParser.Exchange> exchanges, Writer out)
    throws IOException
    {
        for (var exchange : exchanges)
        {
            reportSingle(exchange, out);
        }
    }

    private void reportSingle(HttpExchangeParser.Exchange exchange, Writer out)
    throws IOException
    {
        out.append("Request: ").append(exchange.getRequest().toString()).append('\n');
        for (Header header : exchange.getRequest().getHeaders())
        {
            out.append(header.toString());
            out.append('\n');
        }
        if (exchange.getRequest().getEntity() != null)
            reportBody(ContentType.parseLenient(exchange.getRequest().getEntity().getContentType()), exchange.getRequest().getEntity().getContent(), out);

        out.append("Response: ").append(exchange.getResponse().toString()).append('\n');
        for (Header header : exchange.getResponse().getHeaders())
        {
            out.append(header.toString());
            out.append('\n');
        }
        if (exchange.getResponse().getEntity() != null)
            reportBody(ContentType.parseLenient(exchange.getResponse().getEntity().getContentType()), exchange.getResponse().getEntity().getContent(), out);

        out.write('\n');
    }

    protected void reportBody(ContentType contentType, InputStream data, Writer out)
    throws IOException
    {
        Charset charset;
        if (contentType == null)
            charset = StandardCharsets.UTF_8;
        else
            charset = contentType.getCharset(StandardCharsets.UTF_8);

        try (InputStreamReader in = new InputStreamReader(data, charset))
        {
            in.transferTo(out);
        }
        out.write('\n');
    }
}
