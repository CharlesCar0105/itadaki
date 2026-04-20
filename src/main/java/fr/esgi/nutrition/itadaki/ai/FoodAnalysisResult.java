package fr.esgi.nutrition.itadaki.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodAnalysisResult {

    @JsonProperty("dish_name")
    private String dishName;

    @JsonProperty("portion_size")
    private String portionSize;

    private Double confidence;

    @JsonProperty("total_calories")
    private Integer totalCalories;

    @JsonProperty("total_protein_g")
    private Double totalProteinG;

    @JsonProperty("total_fat_g")
    private Double totalFatG;

    @JsonProperty("total_carbs_g")
    private Double totalCarbsG;

    @JsonProperty("health_score")
    private Integer healthScore;

    private String advice;

    @JsonProperty("is_balanced")
    private Boolean isBalanced;

    private List<IngredientDto> ingredients;

    @JsonProperty("mock_mode")
    private Boolean mockMode;
}
