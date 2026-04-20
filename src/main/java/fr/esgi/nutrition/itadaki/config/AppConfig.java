package fr.esgi.nutrition.itadaki.config;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class AppConfig {

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    /**
     * RestTemplate utilisé par VisionService (appels Ollama /api/generate).
     * Inclut le header ngrok-skip-browser-warning pour passer le tunnel ngrok.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(300))
                .additionalInterceptors((request, body, execution) -> {
                    request.getHeaders().set("ngrok-skip-browser-warning", "true");
                    return execution.execute(request, body);
                })
                .build();
    }

    /**
     * OllamaApi utilisé par Spring AI ChatClient (CalorieService / synthèse).
     * Ajoute le header ngrok sur les deux clients HTTP internes.
     */
    @Bean
    public OllamaApi ollamaApi() {
        RestClient.Builder restClientBuilder = RestClient.builder()
                .defaultHeader("ngrok-skip-browser-warning", "true");
        WebClient.Builder webClientBuilder = WebClient.builder()
                .defaultHeader("ngrok-skip-browser-warning", "true");
        return new OllamaApi(ollamaBaseUrl, restClientBuilder, webClientBuilder);
    }
}
