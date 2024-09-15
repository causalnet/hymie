package au.net.causal.hymie.filter;

import org.apache.hc.core5.http.MessageHeaders;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class CompositeContentFilter implements  ContentFilter
{
    private final List<? extends ContentFilter> filters;

    public CompositeContentFilter(List<? extends ContentFilter> filters)
    {
        this.filters = List.copyOf(filters);
    }

    @Override
    public InputStream applyFilter(InputStream content, MessageHeaders headers) throws IOException
    {
        for (ContentFilter filter : filters)
        {
            content = filter.applyFilter(content, headers);
        }

        return content;
    }
}
