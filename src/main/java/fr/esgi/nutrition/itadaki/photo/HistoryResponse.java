package fr.esgi.nutrition.itadaki.photo;

import java.time.LocalDateTime;

public record HistoryResponse(
        Long id,
        String filename,
        LocalDateTime uploadedAt,
        String analysisResult,
        Integer calories
) {
}
