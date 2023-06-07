package au.net.causal.hymie.spring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.netty.channel.ChannelOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.util.Locale;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
                classes = SpringExerciser.TestConfiguration.class,
                properties = {"server.port=8080"}
)
@AutoConfigureMockMvc
class SpringExerciser
{
    @LocalServerPort
    private int serverPort;

    private final HttpClient httpClient = HttpClient.create()
                                                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);

    private final WebClient webClient = WebClient.builder()
                                                 .clientConnector(new ReactorClientHttpConnector(httpClient))
                                                 .build();

    @Test
    void testText()
    {
        var r = webClient.get().uri(URI.create("http://localhost:" + serverPort + "/hello"));

        String response =
                r.retrieve()
                 .bodyToMono(String.class)
                 .block();

        assertThat(response).isEqualTo("Good morning, what will be for eating?");
    }

    @Test
    void testJson()
    {
        var r = webClient.get().uri(URI.create("http://localhost:" + serverPort + "/hellojson"));

        HelloJson response =
                    r.retrieve()
                     .bodyToMono(HelloJson.class)
                     .block();

        assertThat(response.getMessage()).isEqualTo("Good morning, what will be for eating?");
        assertThat(response.getAdditional()).isEqualTo("This is something more");
    }

    @Test
    void testPost()
    {
        var r = webClient.post().uri(URI.create("http://localhost:" + serverPort + "/uppercase"));
        HelloJson response =
                    r.contentType(MediaType.APPLICATION_JSON)
                     .bodyValue(new HelloJson("Greetings", "More data"))
                     .retrieve()
                     .bodyToMono(HelloJson.class)
                     .block();

        assertThat(response.getMessage()).isEqualTo("GREETINGS");
        assertThat(response.getAdditional()).isEqualTo("MORE DATA");
    }

    @SpringBootApplication
    public static class TestConfiguration
    {
        @Bean
        public MyRestController myRestController()
        {
            return new MyRestController();
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

        @GetMapping("/hellojson")
        public HelloJson helloJson()
        {
            return new HelloJson(hello(), "This is something more");
        }

        @PostMapping("/uppercase")
        public HelloJson uppercase(@RequestBody HelloJson input)
        {
            return new HelloJson(input.getMessage().toUpperCase(Locale.ROOT), input.getAdditional().toUpperCase(Locale.ROOT));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HelloJson
    {
        private String message;
        private String additional;

        public HelloJson()
        {
        }

        public HelloJson(String message, String additional)
        {
            this.message = message;
            this.additional = additional;
        }

        public String getMessage()
        {
            return message;
        }

        public String getAdditional()
        {
            return additional;
        }

        public void setMessage(String message)
        {
            this.message = message;
        }

        public void setAdditional(String additional)
        {
            this.additional = additional;
        }
    }
}
