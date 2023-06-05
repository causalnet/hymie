package au.net.causal.hymie;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;

public interface TrafficReporter
{
    public void report(Collection<? extends HttpExchangeParser.Exchange> exchanges, Writer out)
    throws IOException;
}
