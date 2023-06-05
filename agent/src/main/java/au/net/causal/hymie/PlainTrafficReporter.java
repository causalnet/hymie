package au.net.causal.hymie;

import org.apache.hc.core5.http.Header;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
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
        StringBuilder buf = new StringBuilder();

        buf.append("Request: ").append(exchange.getRequest()).append('\n');
        for (Header header : exchange.getRequest().getHeaders())
        {
            buf.append(header);
            buf.append('\n');
        }
        if (exchange.getRequest().getEntity() != null)
        {
            try (ByteArrayOutputStream body = new ByteArrayOutputStream())
            {
                exchange.getRequest().getEntity().writeTo(body);
                String bodyString = body.toString(StandardCharsets.UTF_8);
                buf.append(bodyString);
                buf.append('\n');
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        buf.append("Response: ").append(exchange.getResponse()).append('\n');
        for (Header header : exchange.getResponse().getHeaders())
        {
            buf.append(header);
            buf.append('\n');
        }
        if (exchange.getResponse().getEntity() != null)
        {
            try (ByteArrayOutputStream body = new ByteArrayOutputStream())
            {
                exchange.getResponse().getEntity().writeTo(body);
                String bodyString = body.toString(StandardCharsets.UTF_8);
                buf.append(bodyString);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        out.write(buf.toString());
        out.write("\n");
    }
}
