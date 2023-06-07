package au.net.causal.hymie.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Message
{
    private Map<String, List<String>> headers;
    private byte[] body;

    public Map<String, List<String>> getHeaders()
    {
        return headers;
    }

    public void setHeaders(Map<String, List<String>> headers)
    {
        this.headers = headers;
    }

    public byte[] getBody()
    {
        return body;
    }

    public void setBody(byte[] body)
    {
        this.body = body;
    }
}
