package au.net.causal.hymie.sslsocket;

import au.net.causal.hymie.it.AbstractSslNetworkExerciser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
                classes = NetworkExerciser.TestConfiguration.class
)
class NetworkExerciser extends AbstractSslNetworkExerciser
{
    @Test
    void testText()
    throws Exception
    {
        URLConnection c = new URI("https://localhost:" + serverPort + "/hello").toURL().openConnection();
        configureSsl(c);

        try (InputStream is = c.getInputStream())
        {
            String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(response).isEqualTo("Good morning, what will be for eating?");
        }

        if (c instanceof HttpURLConnection hc)
            hc.disconnect();
    }

    @Test
    void testPost()
    throws Exception
    {
        URLConnection c = new URI("https://localhost:" + serverPort + "/uppercasehello").toURL().openConnection();
        configureSsl(c);
        c.setDoOutput(true);
        c.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE);
        c.setRequestProperty(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        try (OutputStream os = c.getOutputStream())
        {
            os.write("Good morning, what will be for eating?".getBytes(StandardCharsets.UTF_8));
        }

        try (InputStream is = c.getInputStream())
        {
            String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            response = URLDecoder.decode(response, StandardCharsets.UTF_8);
            assertThat(response).isEqualTo("GOOD MORNING, WHAT WILL BE FOR EATING?");
        }

        if (c instanceof HttpURLConnection hc)
            hc.disconnect();
    }

    @SpringBootApplication
    public static class TestConfiguration
    {
        @Bean
        public MyRestController myRestController()
        {
            return new MyRestController();
        }

        @Bean
        @Order(0)
        public TomcatConnectorCustomizer myTomcatCustomizer()
        {
            return new TomcatSslCustomizer();

        }
    }

    @RestController
    public static class MyRestController
    {
        @GetMapping("/hello")
        public String hello()
        {
            return "Good morning, what will be for eating?";
        }

        @PostMapping(value = "/uppercasehello", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
        public String uppercase(@RequestBody String input)
        {
            return input.toUpperCase(Locale.ROOT);
        }
    }
}
