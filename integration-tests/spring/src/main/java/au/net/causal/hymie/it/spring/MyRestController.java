package au.net.causal.hymie.it.spring;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MyRestController
{
    @GetMapping("/hello")
    public String hello()
    {
        return "Good morning, what will be for eating?";
    }
}