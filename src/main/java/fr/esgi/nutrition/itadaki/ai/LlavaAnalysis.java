package fr.esgi.nutrition.itadaki.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlavaAnalysis {

    @JsonProperty("dish_name")
    private String dishName;

    private List<IngredientDto> ingredients;

    @JsonProperty("portion_size")
    private String portionSize;

    @JsonProperty("overall_confidence")
    private Double overallConfidence;
}
