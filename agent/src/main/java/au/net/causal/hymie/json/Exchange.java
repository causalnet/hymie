package au.net.causal.hymie.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Exchange
{
    private String address;
    private Request request;
    private Response response;

    public String getAddress()
    {
        return address;
    }

    public void setAddress(String address)
    {
        this.address = address;
    }

    public Request getRequest()
    {
        return request;
    }

    public void setRequest(Request request)
    {
        this.request = request;
    }

    public Response getResponse()
    {
        return response;
    }

    public void setResponse(Response response)
    {
        this.response = response;
    }
}
