package fr.esgi.nutrition.itadaki.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    @Value("${rag.service.url:http://localhost:5000}")
    private String ragServiceUrl;

    @Value("${app.mock-mode:false}")
    private boolean mockMode;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public RagService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public void enrichWithNutrition(List<IngredientDto> ingredients) {
        if (mockMode) {
            enrichWithMockData(ingredients);
            return;
        }
        try {
            restTemplate.getForObject(ragServiceUrl + "/health", String.class);
            List<String> names = ingredients.stream().map(IngredientDto::getName).toList();
            Map<String, Object> body = Map.of("ingredients", names);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    ragServiceUrl + "/search", body, Map.class);

            if (response == null) {
                enrichWithMockData(ingredients);
                return;
            }

            for (IngredientDto ingredient : ingredients) {
                Object rawData = response.get(ingredient.getName());
                if (rawData instanceof Map<?, ?>) {
                    try {
                        Map<String, Object> data = objectMapper.convertValue(rawData, new TypeReference<>() {});
                        ingredient.setEnergyKcal(toDouble(data.get("energy_100g")));
                        ingredient.setProteinG(toDouble(data.get("protein_100g")));
                        ingredient.setFatG(toDouble(data.get("fat_100g")));
                        ingredient.setCarbsG(toDouble(data.get("carbs_100g")));
                        if (ingredient.getEnergyKcal() != null && ingredient.getEstimatedWeightG() != null) {
                            ingredient.setCaloriesTotal(
                                    ingredient.getEnergyKcal() * ingredient.getEstimatedWeightG() / 100.0);
                        }
                    } catch (Exception e) {
                        log.warn("Erreur parsing nutrition pour '{}': {}", ingredient.getName(), e.getMessage());
                        applyFallback(ingredient);
                    }
                } else {
                    applyFallback(ingredient);
                }
            }
        } catch (RestClientException e) {
            log.warn("RAG indisponible, valeurs par défaut ({})", e.getMessage());
            enrichWithMockData(ingredients);
        }
    }

    private void enrichWithMockData(List<IngredientDto> ingredients) {
        Map<String, double[]> table = getNutritionTable();
        for (IngredientDto ingredient : ingredients) {
            String key = findBestKey(ingredient.getName(), table);
            if (key != null) {
                double[] v = table.get(key);
                ingredient.setEnergyKcal(v[0]);
                ingredient.setProteinG(v[1]);
                ingredient.setFatG(v[2]);
                ingredient.setCarbsG(v[3]);
            } else {
                applyFallback(ingredient);
            }
            if (ingredient.getEnergyKcal() != null && ingredient.getEstimatedWeightG() != null) {
                ingredient.setCaloriesTotal(ingredient.getEnergyKcal() * ingredient.getEstimatedWeightG() / 100.0);
            }
        }
    }

    private void applyFallback(IngredientDto ingredient) {
        ingredient.setEnergyKcal(100.0);
        ingredient.setProteinG(5.0);
        ingredient.setFatG(3.0);
        ingredient.setCarbsG(15.0);
        if (ingredient.getEstimatedWeightG() != null) {
            ingredient.setCaloriesTotal(ingredient.getEnergyKcal() * ingredient.getEstimatedWeightG() / 100.0);
        }
    }

    private Map<String, double[]> getNutritionTable() {
        return Map.ofEntries(
                Map.entry("riz blanc",                  new double[]{130,  2.7,  0.3, 28.6}),
                Map.entry("riz à sushi",                new double[]{145,  3.0,  0.3, 32.0}),
                Map.entry("pâtes",                      new double[]{370, 13.0,  1.5, 72.0}),
                Map.entry("pain",                       new double[]{265,  9.0,  3.2, 49.0}),
                Map.entry("pain grillé",                new double[]{280, 10.0,  4.0, 50.0}),
                Map.entry("pâte à pizza",               new double[]{270,  8.0,  3.0, 54.0}),
                Map.entry("pomme de terre",             new double[]{ 77,  2.0,  0.1, 17.0}),
                Map.entry("purée de pommes de terre",   new double[]{ 85,  2.0,  4.0, 12.0}),
                Map.entry("carotte",                    new double[]{ 41,  0.9,  0.2,  9.6}),
                Map.entry("brocoli",                    new double[]{ 34,  2.8,  0.4,  6.6}),
                Map.entry("tomate",                     new double[]{ 18,  0.9,  0.2,  3.9}),
                Map.entry("salade verte",               new double[]{ 15,  1.4,  0.2,  2.9}),
                Map.entry("oignon",                     new double[]{ 40,  1.1,  0.1,  9.3}),
                Map.entry("champignon",                 new double[]{ 22,  3.1,  0.3,  3.3}),
                Map.entry("courgette",                  new double[]{ 17,  1.2,  0.3,  3.1}),
                Map.entry("poivron",                    new double[]{ 31,  1.0,  0.3,  6.0}),
                Map.entry("concombre",                  new double[]{ 15,  0.7,  0.1,  3.6}),
                Map.entry("avocat",                     new double[]{160,  2.0, 15.0,  9.0}),
                Map.entry("épinards",                   new double[]{ 23,  2.9,  0.4,  3.6}),
                Map.entry("poulet",                     new double[]{165, 31.0,  3.6,  0.0}),
                Map.entry("boeuf",                      new double[]{271, 26.0, 18.0,  0.0}),
                Map.entry("porc",                       new double[]{242, 27.0, 14.0,  0.0}),
                Map.entry("bacon",                      new double[]{540, 37.0, 42.0,  0.0}),
                Map.entry("saucisse",                   new double[]{300, 12.0, 27.0,  1.0}),
                Map.entry("jambon",                     new double[]{145, 20.0,  5.0,  2.0}),
                Map.entry("pepperoni",                  new double[]{460, 24.0, 40.0,  1.0}),
                Map.entry("salami",                     new double[]{460, 24.0, 40.0,  1.0}),
                Map.entry("steak haché",                new double[]{271, 26.0, 18.0,  0.0}),
                Map.entry("saumon",                     new double[]{208, 20.0, 13.0,  0.0}),
                Map.entry("thon",                       new double[]{132, 28.0,  1.0,  0.0}),
                Map.entry("crevette",                   new double[]{ 99, 21.0,  1.1,  0.0}),
                Map.entry("fromage",                    new double[]{400, 25.0, 33.0,  1.3}),
                Map.entry("mozzarella",                 new double[]{280, 18.0, 22.0,  2.0}),
                Map.entry("gruyère",                    new double[]{413, 29.0, 32.0,  0.4}),
                Map.entry("parmesan",                   new double[]{431, 38.0, 29.0,  3.2}),
                Map.entry("beurre",                     new double[]{717,  0.9, 81.0,  0.1}),
                Map.entry("oeuf au plat",               new double[]{185, 14.0, 14.0,  0.4}),
                Map.entry("oeuf brouillé",              new double[]{155, 11.0, 12.0,  1.0}),
                Map.entry("sauce soja",                 new double[]{ 60,  8.0,  0.1,  5.6}),
                Map.entry("sauce tomate",               new double[]{ 40,  1.5,  0.5,  8.0}),
                Map.entry("mayonnaise",                 new double[]{680,  1.0, 75.0,  1.0}),
                Map.entry("nori",                       new double[]{ 35,  5.8,  0.3,  5.1}),
                Map.entry("wasabi",                     new double[]{109,  6.2,  0.6, 23.5}),
                Map.entry("gingembre mariné",           new double[]{ 70,  0.2,  0.1, 16.0})
        );
    }

    private String findBestKey(String name, Map<String, double[]> table) {
        String lower = name.toLowerCase().trim();
        for (String prefix : List.of("sauté ", "fried ", "grilled ", "baked ", "fresh ", "raw ", "cooked ")) {
            if (lower.startsWith(prefix)) { lower = lower.substring(prefix.length()).trim(); break; }
        }
        if (table.containsKey(lower)) return lower;
        for (String key : table.keySet()) {
            if (lower.contains(key) || key.contains(lower)) return key;
        }
        return null;
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try { return Double.parseDouble(value.toString()); } catch (NumberFormatException e) { return null; }
    }
}
