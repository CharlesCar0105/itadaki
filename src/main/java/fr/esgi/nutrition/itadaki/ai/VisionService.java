package fr.esgi.nutrition.itadaki.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VisionService {

    private static final Logger log = LoggerFactory.getLogger(VisionService.class);

    private static final String VISION_PROMPT = """
            Analyse cette photo de repas et réponds UNIQUEMENT avec un objet JSON valide.
            Ne mets aucun texte avant ni après le JSON. Commence directement par {

            Contraintes strictes :
            - dish_name : nom du plat en français
            - ingredients[].name : noms en français, simples, sans adjectifs de cuisson
            - ingredients[].estimated_weight_g : poids estimé en grammes (nombre entier)
            - ingredients[].confidence : entre 0.0 et 1.0
            - portion_size : exactement "petite" OU "normale" OU "grande"
            - overall_confidence : entre 0.0 et 1.0
            - Liste SEULEMENT les aliments visibles. N'invente rien.
            - Ignore les boissons, couverts et vaisselle.

            Vocabulaire imposé (utilise ces termes exacts) :
            Féculents : riz blanc, riz à sushi, pâtes, pain, pain grillé, pâte à pizza, pomme de terre, purée de pommes de terre
            Viandes : poulet, boeuf, porc, jambon, bacon, saucisse, steak haché
            Poissons : saumon, thon, crevette
            Légumes : tomate, salade verte, concombre, carotte, oignon, poivron, brocoli, champignon, courgette, avocat, épinards, maïs, petits pois, haricots verts
            Fromages : mozzarella, gruyère, parmesan, fromage, fromage râpé, fromage fondu
            Oeufs : oeuf au plat, oeuf brouillé
            Charcuterie : pepperoni, salami
            Sushi : riz à sushi, nori, wasabi, gingembre mariné
            Sauces & matières grasses : sauce tomate, crème fraîche, crème liquide, béchamel, sauce soja, sauce curry, sauce bolognaise, mayonnaise, ketchup, moutarde, vinaigrette, huile d'olive, beurre, pesto, sauce barbecue, sauce fromagère
            Légumineuses & autres : lentilles, pois chiches, haricots rouges, tofu, oeuf dur

            Format de réponse :
            {"dish_name":"...","ingredients":[{"name":"...","estimated_weight_g":100,"confidence":0.9}],"portion_size":"normale","overall_confidence":0.85}
            """;

    private static final Map<String, String> NORMALIZATION_MAP = Map.ofEntries(
            Map.entry("lettuce",           "salade verte"),
            Map.entry("tomato",            "tomate"),
            Map.entry("tomatoes",          "tomate"),
            Map.entry("onion",             "oignon"),
            Map.entry("mushroom",          "champignon"),
            Map.entry("egg",               "oeuf au plat"),
            Map.entry("fried egg",         "oeuf au plat"),
            Map.entry("scrambled eggs",    "oeuf brouillé"),
            Map.entry("potato",            "pomme de terre"),
            Map.entry("mashed potatoes",   "purée de pommes de terre"),
            Map.entry("chicken",           "poulet"),
            Map.entry("rice",              "riz blanc"),
            Map.entry("white rice",        "riz blanc"),
            Map.entry("sushi rice",        "riz à sushi"),
            Map.entry("salmon",            "saumon"),
            Map.entry("tuna",              "thon"),
            Map.entry("shrimp",            "crevette"),
            Map.entry("cheese",            "fromage"),
            Map.entry("mozzarella cheese", "mozzarella"),
            Map.entry("bacon",             "bacon"),
            Map.entry("sausage",           "saucisse"),
            Map.entry("carrot",            "carotte"),
            Map.entry("cucumber",          "concombre"),
            Map.entry("avocado",           "avocat"),
            Map.entry("bread",             "pain"),
            Map.entry("toast",             "pain grillé"),
            Map.entry("pepper",            "poivron"),
            Map.entry("bell pepper",       "poivron"),
            Map.entry("broccoli",          "brocoli"),
            Map.entry("zucchini",          "courgette"),
            Map.entry("ham",               "jambon"),
            Map.entry("pepperoni",         "pepperoni"),
            Map.entry("pasta",             "pâtes"),
            Map.entry("beef",              "boeuf"),
            Map.entry("pork",              "porc"),
            Map.entry("nori",              "nori"),
            Map.entry("wasabi",            "wasabi"),
            Map.entry("ginger",            "gingembre mariné"),
            // Sauces & matières grasses
            Map.entry("cream",             "crème fraîche"),
            Map.entry("heavy cream",       "crème liquide"),
            Map.entry("whipping cream",    "crème liquide"),
            Map.entry("sour cream",        "crème fraîche"),
            Map.entry("tomato sauce",      "sauce tomate"),
            Map.entry("bolognese",         "sauce bolognaise"),
            Map.entry("bechamel",          "béchamel"),
            Map.entry("béchamel sauce",    "béchamel"),
            Map.entry("white sauce",       "béchamel"),
            Map.entry("curry sauce",       "sauce curry"),
            Map.entry("soy sauce",         "sauce soja"),
            Map.entry("mayonnaise",        "mayonnaise"),
            Map.entry("ketchup",           "ketchup"),
            Map.entry("mustard",           "moutarde"),
            Map.entry("dressing",          "vinaigrette"),
            Map.entry("olive oil",         "huile d'olive"),
            Map.entry("oil",               "huile d'olive"),
            Map.entry("butter",            "beurre"),
            Map.entry("pesto",             "pesto"),
            Map.entry("bbq sauce",         "sauce barbecue"),
            Map.entry("barbecue sauce",    "sauce barbecue"),
            Map.entry("cheese sauce",      "sauce fromagère"),
            // Légumes supplémentaires
            Map.entry("spinach",           "épinards"),
            Map.entry("corn",              "maïs"),
            Map.entry("peas",              "petits pois"),
            Map.entry("green beans",       "haricots verts"),
            Map.entry("carrots",           "carotte"),
            // Légumineuses
            Map.entry("lentils",           "lentilles"),
            Map.entry("chickpeas",         "pois chiches"),
            Map.entry("kidney beans",      "haricots rouges"),
            Map.entry("tofu",              "tofu"),
            Map.entry("hard boiled egg",   "oeuf dur"),
            Map.entry("boiled egg",        "oeuf dur")
    );

    private static final List<String> COOKING_PREFIXES = List.of(
            "sautéed ", "sauteed ", "sauté ", "fried ", "grilled ", "baked ",
            "steamed ", "roasted ", "crispy ", "poached ", "boiled ",
            "fresh ", "raw ", "cooked ", "sliced ", "diced ", "chopped ",
            "smoked ", "braised ", "pan-fried ", "deep-fried "
    );

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${app.llava.model:llava:7b-q4_0}")
    private String llavaModel;

    @Value("${app.mock-mode:false}")
    private boolean mockMode;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public VisionService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    private byte[] resizeImage(byte[] imageBytes, int maxSize) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img == null) return imageBytes;
            int w = img.getWidth(), h = img.getHeight();
            if (w <= maxSize && h <= maxSize) return imageBytes;
            double scale = Math.min((double) maxSize / w, (double) maxSize / h);
            int nw = (int) (w * scale), nh = (int) (h * scale);
            BufferedImage resized = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.drawImage(img, 0, 0, nw, nh, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", out);
            log.info("Image redimensionnée: {}x{} -> {}x{} ({} -> {} bytes)", w, h, nw, nh, imageBytes.length, out.size());
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("Erreur redimensionnement, envoi original: {}", e.getMessage());
            return imageBytes;
        }
    }

    public LlavaAnalysis analyzeImage(byte[] imageBytes) {
        if (mockMode) {
            log.info("[MOCK] Returning mock vision analysis");
            return getMockAnalysis();
        }
        imageBytes = resizeImage(imageBytes, 512);
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        log.info("Image: {} bytes raw, {} chars base64", imageBytes.length, base64.length());
        LlavaAnalysis result = callVisionModel(VISION_PROMPT, base64, 2);
        return normalizeAnalysis(result);
    }

    public LlavaAnalysis refineAnalysis(byte[] imageBytes, LlavaAnalysis previous, String userFeedback) {
        if (mockMode) {
            LlavaAnalysis mock = getMockAnalysis();
            mock.setDishName(mock.getDishName() + " (raffiné)");
            return mock;
        }
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        try {
            String previousJson = objectMapper.writeValueAsString(previous);
            String prompt = String.format("""
                    Tu avais analysé cette assiette comme : %s
                    L'utilisateur te corrige : "%s"
                    Tiens compte de cette correction et réévalue l'image.
                    Réponds UNIQUEMENT en JSON valide, en français, avec le même format qu'avant.
                    """, previousJson, userFeedback);
            LlavaAnalysis result = callVisionModel(prompt, base64, 2);
            return normalizeAnalysis(result);
        } catch (Exception e) {
            log.error("Erreur sérialisation previous analysis", e);
            return callVisionModel(VISION_PROMPT, base64, 2);
        }
    }

    private LlavaAnalysis normalizeAnalysis(LlavaAnalysis analysis) {
        if (analysis == null || analysis.getIngredients() == null) return analysis;
        for (IngredientDto ingredient : analysis.getIngredients()) {
            if (ingredient.getName() != null) {
                String normalized = normalizeIngredientName(ingredient.getName());
                if (!normalized.equals(ingredient.getName())) {
                    log.debug("Normalisé: '{}' → '{}'", ingredient.getName(), normalized);
                }
                ingredient.setName(normalized);
            }
        }
        return analysis;
    }

    private String normalizeIngredientName(String name) {
        String lower = name.toLowerCase().trim();
        for (String prefix : COOKING_PREFIXES) {
            if (lower.startsWith(prefix)) {
                lower = lower.substring(prefix.length()).trim();
                break;
            }
        }
        if (NORMALIZATION_MAP.containsKey(lower)) return NORMALIZATION_MAP.get(lower);
        for (Map.Entry<String, String> entry : NORMALIZATION_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) return entry.getValue();
        }
        if (lower.isEmpty()) return name;
        return lower.substring(0, 1).toUpperCase() + lower.substring(1);
    }

    @SuppressWarnings("unchecked")
    private LlavaAnalysis callVisionModel(String prompt, String base64Image, int retriesLeft) {
        try {
            Map<String, Object> options = Map.of(
                    "num_predict", 512,
                    "temperature", 0.1,
                    "num_ctx", 4096
            );
            Map<String, Object> body = new HashMap<>();
            body.put("model", llavaModel);
            body.put("prompt", prompt);
            body.put("images", List.of(base64Image));
            body.put("stream", false);
            body.put("options", options);

            String bodyJson = objectMapper.writeValueAsString(body);
            log.info("Appel vision model={}, body size={} bytes", llavaModel, bodyJson.length());
            Map<?, ?> response = restTemplate.postForObject(
                    ollamaBaseUrl + "/api/generate", body, Map.class);

            if (response == null || !response.containsKey("response")) {
                throw new RuntimeException("Réponse Ollama vide");
            }

            String rawText = (String) response.get("response");
            log.debug("Vision brut: {}", rawText);
            return parseJson(rawText);

        } catch (JsonParseException e) {
            if (retriesLeft > 0) {
                log.warn("JSON invalide, retry ({} restant): {}", retriesLeft, e.getMessage());
                String stricterPrompt = prompt + "\n\nATTENTION: réponds UNIQUEMENT avec le JSON. Commence par { termine par }.";
                return callVisionModel(stricterPrompt, base64Image, retriesLeft - 1);
            }
            log.error("Vision: JSON invalide après plusieurs tentatives", e);
            return getMockAnalysis();
        } catch (Exception e) {
            log.error("Erreur appel vision ({}): {}", llavaModel, e.getMessage());
            if (retriesLeft > 0) return callVisionModel(prompt, base64Image, retriesLeft - 1);
            return getMockAnalysis();
        }
    }

    private LlavaAnalysis parseJson(String rawText) throws JsonParseException {
        try {
            return objectMapper.readValue(rawText.trim(), LlavaAnalysis.class);
        } catch (Exception e) {
            String extracted = extractJson(rawText);
            if (extracted != null) {
                try {
                    return objectMapper.readValue(extracted, LlavaAnalysis.class);
                } catch (Exception e2) {
                    throw new JsonParseException("JSON extrait invalide: " + extracted, e2);
                }
            }
            throw new JsonParseException("Aucun JSON trouvé dans: " + rawText, e);
        }
    }

    private String extractJson(String text) {
        Pattern pattern = Pattern.compile("\\{.*}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private LlavaAnalysis getMockAnalysis() {
        return LlavaAnalysis.builder()
                .dishName("Riz sauté au poulet (MOCK)")
                .portionSize("normale")
                .overallConfidence(0.85)
                .ingredients(List.of(
                        IngredientDto.builder().name("riz blanc").estimatedWeightG(200.0).confidence(0.95).build(),
                        IngredientDto.builder().name("poulet").estimatedWeightG(150.0).confidence(0.88).build(),
                        IngredientDto.builder().name("sauce soja").estimatedWeightG(20.0).confidence(0.70).build(),
                        IngredientDto.builder().name("oignon").estimatedWeightG(50.0).confidence(0.80).build()
                ))
                .build();
    }

    static class JsonParseException extends Exception {
        JsonParseException(String msg, Throwable cause) { super(msg, cause); }
    }
}
