package au.net.causal.hymie.filter;

import java.util.List;

public final class ContentFilters
{
    public static final List<ContentFilter> FILTERS = List.of(
            new GzipContentFilter()
    );

    private ContentFilters()
    {
    }
}
