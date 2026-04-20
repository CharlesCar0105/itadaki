package fr.esgi.nutrition.itadaki.photo;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record HistoryResponse(
        Long id,
        String filename,
        LocalDateTime uploadedAt,
        LocalDate mealDate,
        MealPhotoStatus status,
        String preliminaryAnalysis,
        String finalAnalysis,
        Integer calories
) {
}
