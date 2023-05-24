package au.net.causal.hymie.it.spring;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.URI;

public class HymieTester
{
    public static void main(String... args)
    {
        HttpClient httpClient = HttpClient.create()
                                          .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);

        WebClient client = WebClient.builder()
                                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                                    .build();

        String response =
            client.get()
                  .uri(URI.create("https://www.google.com"))
                  .retrieve()
                  .bodyToMono(String.class)
                  .block();

        System.out.println("Response: " + response);
    }
}
