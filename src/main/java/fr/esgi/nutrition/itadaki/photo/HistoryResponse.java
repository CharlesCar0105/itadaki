package fr.esgi.nutrition.itadaki.photo;

import java.time.LocalDateTime;

public record HistoryResponse(
        Long id,
        String filename,
        LocalDateTime uploadedAt,
        MealPhotoStatus status,
        String preliminaryAnalysis,
        String finalAnalysis,
        Integer calories
) {
}
