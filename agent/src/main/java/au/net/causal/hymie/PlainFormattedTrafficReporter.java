package au.net.causal.hymie;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.core5.http.ContentType;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

public class PlainFormattedTrafficReporter extends PlainTrafficReporter
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void reportBody(ContentType contentType, InputStream data, Writer out) throws IOException
    {
        if (ContentType.APPLICATION_JSON.isSameMimeType(contentType))
            out.write(objectMapper.readTree(data).toPrettyString());
        else
            super.reportBody(contentType, data, out);
    }
}
