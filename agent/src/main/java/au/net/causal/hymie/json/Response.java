package au.net.causal.hymie.json;

public class Response extends Message
{
    private int statusCode;
    private String reasonPhrase;

    public int getStatusCode()
    {
        return statusCode;
    }

    public void setStatusCode(int statusCode)
    {
        this.statusCode = statusCode;
    }

    public String getReasonPhrase()
    {
        return reasonPhrase;
    }

    public void setReasonPhrase(String reasonPhrase)
    {
        this.reasonPhrase = reasonPhrase;
    }
}
