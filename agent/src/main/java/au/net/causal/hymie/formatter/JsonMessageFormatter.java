package au.net.causal.hymie.formatter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.core5.http.ContentType;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

public class JsonMessageFormatter implements MessageFormatter
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void format(ContentType contentType, InputStream data, Writer formatted) throws IOException
    {
        formatted.write(objectMapper.readTree(data).toPrettyString());
    }

    @Override
    public boolean supportsContentType(ContentType contentType)
    {
        return ContentType.APPLICATION_JSON.isSameMimeType(contentType);

    }
}
