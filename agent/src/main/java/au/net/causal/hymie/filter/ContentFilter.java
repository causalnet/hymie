package au.net.causal.hymie.filter;

import org.apache.hc.core5.http.MessageHeaders;

import java.io.IOException;
import java.io.InputStream;

public interface ContentFilter
{
    public InputStream applyFilter(InputStream content, MessageHeaders headers)
    throws IOException;
}
