package fr.esgi.nutrition.itadaki.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CalorieService {

    private static final Logger log = LoggerFactory.getLogger(CalorieService.class);

    private static final String SYNTHESIS_PROMPT_TEMPLATE = """
            Tu es un nutritionniste. Évalue la qualité nutritionnelle de ce repas.

            Plat : %s
            Portion : %s

            Ingrédients :
            %s

            Les totaux ont déjà été calculés (utilise ces valeurs EXACTES) :
            - Calories totales : %d kcal
            - Protéines : %.1f g
            - Lipides : %.1f g
            - Glucides : %.1f g

            Réponds UNIQUEMENT en JSON valide, sans texte avant ni après :
            {
              "total_calories": %d,
              "total_protein_g": %.1f,
              "total_fat_g": %.1f,
              "total_carbs_g": %.1f,
              "health_score": 7,
              "advice": "conseil court en 1-2 phrases en français",
              "is_balanced": true
            }

            Remplace SEULEMENT health_score (entier 1-10) et advice (conseil bref en français).
            is_balanced : true si protéines bien représentées ET au moins un légume visible.
            NE CHANGE PAS les valeurs numériques de calories, protéines, lipides, glucides.
            """;

    @Value("${app.mock-mode:false}")
    private boolean mockMode;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public CalorieService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public FoodAnalysisResult synthesize(LlavaAnalysis llavaAnalysis, List<IngredientDto> enrichedIngredients) {
        if (mockMode) {
            return buildMockResult(llavaAnalysis, enrichedIngredients);
        }

        int totalKcal = (int) Math.round(
                enrichedIngredients.stream()
                        .filter(i -> i.getCaloriesTotal() != null)
                        .mapToDouble(IngredientDto::getCaloriesTotal)
                        .sum());

        double totalProtein = round1(sumNutrient(enrichedIngredients, i ->
                i.getProteinG() != null && i.getEstimatedWeightG() != null
                        ? i.getProteinG() * i.getEstimatedWeightG() / 100 : 0));
        double totalFat = round1(sumNutrient(enrichedIngredients, i ->
                i.getFatG() != null && i.getEstimatedWeightG() != null
                        ? i.getFatG() * i.getEstimatedWeightG() / 100 : 0));
        double totalCarbs = round1(sumNutrient(enrichedIngredients, i ->
                i.getCarbsG() != null && i.getEstimatedWeightG() != null
                        ? i.getCarbsG() * i.getEstimatedWeightG() / 100 : 0));

        String summary = buildSummary(enrichedIngredients);
        String prompt = String.format(SYNTHESIS_PROMPT_TEMPLATE,
                llavaAnalysis.getDishName(), llavaAnalysis.getPortionSize(), summary,
                totalKcal, totalProtein, totalFat, totalCarbs,
                totalKcal, totalProtein, totalFat, totalCarbs);

        log.debug("Appel synthèse ({}kcal, {}g prot)...", totalKcal, totalProtein);
        String raw = callWithRetry(prompt, 2);
        return parseResponse(raw, llavaAnalysis, enrichedIngredients, totalKcal, totalProtein, totalFat, totalCarbs);
    }

    private String callWithRetry(String prompt, int retries) {
        try {
            return chatClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            if (retries > 0) {
                log.warn("Erreur synthèse, retry ({} restant): {}", retries, e.getMessage());
                return callWithRetry(prompt, retries - 1);
            }
            log.error("Synthèse inaccessible après plusieurs tentatives", e);
            return null;
        }
    }

    private FoodAnalysisResult parseResponse(String raw, LlavaAnalysis llava,
                                              List<IngredientDto> ingredients,
                                              int totalKcal, double totalProtein,
                                              double totalFat, double totalCarbs) {
        int healthScore = 5;
        String advice = "";
        boolean isBalanced = totalProtein > 20;

        if (raw != null) {
            try {
                String json = extractJson(raw);
                if (json != null) {
                    var node = objectMapper.readTree(json);
                    healthScore = node.path("health_score").asInt(5);
                    advice = node.path("advice").asText("");
                    isBalanced = node.path("is_balanced").asBoolean(totalProtein > 20);
                }
            } catch (Exception e) {
                log.warn("Erreur parsing réponse synthèse: {}", e.getMessage());
            }
        }

        return FoodAnalysisResult.builder()
                .dishName(llava.getDishName())
                .portionSize(llava.getPortionSize())
                .confidence(llava.getOverallConfidence())
                .totalCalories(totalKcal)
                .totalProteinG(totalProtein)
                .totalFatG(totalFat)
                .totalCarbsG(totalCarbs)
                .healthScore(healthScore)
                .advice(advice)
                .isBalanced(isBalanced)
                .ingredients(ingredients)
                .mockMode(false)
                .build();
    }

    private FoodAnalysisResult buildMockResult(LlavaAnalysis llava, List<IngredientDto> ingredients) {
        return FoodAnalysisResult.builder()
                .dishName(llava.getDishName())
                .portionSize(llava.getPortionSize())
                .confidence(llava.getOverallConfidence())
                .totalCalories(521)
                .totalProteinG(51.7)
                .totalFatG(4.8)
                .totalCarbsG(58.3)
                .healthScore(8)
                .advice("Repas équilibré, riche en protéines. (MOCK)")
                .isBalanced(true)
                .ingredients(ingredients)
                .mockMode(true)
                .build();
    }

    private String buildSummary(List<IngredientDto> ingredients) {
        StringBuilder sb = new StringBuilder();
        for (IngredientDto i : ingredients) {
            sb.append(String.format("- %s : %.0fg, %.0f kcal/100g%n",
                    i.getName(),
                    i.getEstimatedWeightG() != null ? i.getEstimatedWeightG() : 0,
                    i.getEnergyKcal() != null ? i.getEnergyKcal() : 0));
        }
        return sb.toString();
    }

    private String extractJson(String text) {
        Pattern pattern = Pattern.compile("\\{.*}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private double sumNutrient(List<IngredientDto> ingredients, java.util.function.ToDoubleFunction<IngredientDto> fn) {
        return ingredients.stream().mapToDouble(fn).sum();
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
