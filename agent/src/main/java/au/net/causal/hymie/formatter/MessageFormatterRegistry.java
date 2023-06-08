package au.net.causal.hymie.formatter;

import org.apache.hc.core5.http.ContentType;

import java.util.Collection;
import java.util.List;

public class MessageFormatterRegistry
{
    private final List<MessageFormatter> formatters;
    private final MessageFormatter fallback;

    public MessageFormatterRegistry(Collection<? extends MessageFormatter> formatters, MessageFormatter fallback)
    {
        this.formatters = List.copyOf(formatters);
        this.fallback = fallback;
    }

    public MessageFormatter formatter(ContentType contentType)
    {
        return formatters.stream()
                         .filter(f -> f.supportsContentType(contentType))
                         .findFirst()
                         .orElse(fallback);
    }
}
