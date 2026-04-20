package fr.esgi.nutrition.itadaki.photo;

import java.time.LocalDateTime;

public record PhotoUploadResponse(
        Long id,
        String filename,
        LocalDateTime uploadedAt
) {
}
