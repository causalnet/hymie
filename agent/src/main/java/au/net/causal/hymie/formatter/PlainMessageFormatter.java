package au.net.causal.hymie.formatter;

import org.apache.hc.core5.http.ContentType;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class PlainMessageFormatter implements MessageFormatter
{
    @Override
    public void format(ContentType contentType, InputStream data, Writer formatted) throws IOException
    {
        Charset charset;
        if (contentType == null)
            charset = StandardCharsets.UTF_8;
        else
            charset = contentType.getCharset(StandardCharsets.UTF_8);

        try (InputStreamReader in = new InputStreamReader(data, charset))
        {
            in.transferTo(formatted);
        }
    }

    @Override
    public boolean supportsContentType(ContentType contentType)
    {
        return true;
    }

    @Override
    public String getRSyntaxTextAreaStyle(ContentType contentType)
    {
        return null;
    }
}
