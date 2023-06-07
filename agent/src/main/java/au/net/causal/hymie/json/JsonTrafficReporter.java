package au.net.causal.hymie.json;

import au.net.causal.hymie.HttpExchangeParser;
import au.net.causal.hymie.TrafficReporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JsonTrafficReporter implements TrafficReporter
{
    private final ObjectMapper objectMapper;

    public JsonTrafficReporter()
    {
        this(new ObjectMapper());
    }

    public JsonTrafficReporter(ObjectMapper objectMapper)
    {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public void report(Collection<? extends HttpExchangeParser.Exchange> exchanges, Writer out)
    throws IOException
    {
        List<Exchange> jsonExchanges = new ArrayList<>(exchanges.size());
        for (HttpExchangeParser.Exchange exchange : exchanges)
        {
            jsonExchanges.add(toJsonExchange(exchange));
        }
        objectMapper.writeValue(out, jsonExchanges);
        out.write('\n');
    }

    private Exchange toJsonExchange(HttpExchangeParser.Exchange exchange)
    throws IOException
    {
        Exchange jsonExchange = new Exchange();
        Request jsonRequest = new Request();
        Response jsonResponse = new Response();
        jsonExchange.setAddress(exchange.getAddress().toString());
        jsonExchange.setRequest(jsonRequest);
        jsonExchange.setResponse(jsonResponse);

        jsonRequest.setPath(exchange.getRequest().getPath());
        jsonRequest.setMethod(exchange.getRequest().getMethod());
        jsonRequest.setHeaders(toJsonHeaders(exchange.getRequest().getHeaders()));
        jsonRequest.setBody(toJsonBody(exchange.getRequest().getEntity()));

        jsonResponse.setStatusCode(exchange.getResponse().getCode());
        jsonResponse.setReasonPhrase(exchange.getResponse().getReasonPhrase());
        jsonResponse.setHeaders(toJsonHeaders(exchange.getResponse().getHeaders()));
        jsonResponse.setBody(toJsonBody(exchange.getResponse().getEntity()));

        return jsonExchange;
    }

    private Map<String, List<String>> toJsonHeaders(Header[] headers)
    {
        Map<String, List<String>> headerMap = new LinkedHashMap<>();

        for (Header header : headers)
        {
            headerMap.computeIfAbsent(header.getName(), k -> new ArrayList<>()).add(header.getValue());
        }

        return headerMap;
    }

    private byte[] toJsonBody(HttpEntity entity)
    throws IOException
    {
        if (entity == null)
            return null;

        return entity.getContent().readAllBytes();
    }
}
