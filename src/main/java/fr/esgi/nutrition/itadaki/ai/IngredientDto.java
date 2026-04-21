package fr.esgi.nutrition.itadaki.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IngredientDto {

    private String name;

    @JsonProperty("estimated_weight_g")
    private Double estimatedWeightG;

    private Double confidence;

    // Données nutritives (remplies par le RAG)
    @JsonProperty("energy_kcal")
    private Double energyKcal;

    @JsonProperty("protein_g")
    private Double proteinG;

    @JsonProperty("fat_g")
    private Double fatG;

    @JsonProperty("carbs_g")
    private Double carbsG;

    @JsonProperty("calories_total")
    private Double caloriesTotal;
}
