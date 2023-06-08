package au.net.causal.hymie.formatter;

import org.apache.hc.core5.http.ContentType;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

public interface MessageFormatter
{
    public void format(ContentType contentType, InputStream data, Writer formatted)
    throws IOException;

    public boolean supportsContentType(ContentType contentType);
}
