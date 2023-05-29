package au.net.causal.hymie;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

public class TestHymie
{
    //Attach agent before running this one
    public static void main(String... args) throws Exception
    {
        System.out.println("Good morning, what will be for eating?");

        //URLConnection c = new URI("http://localhost:8080").toURL().openConnection();
        URLConnection c = new URI("https://www.google.com.au").toURL().openConnection();

        try (InputStream is = c.getInputStream())
        {
            String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("HTTP response: " + response);
        }

        if (c instanceof HttpURLConnection hc)
            hc.disconnect();
    }
}
