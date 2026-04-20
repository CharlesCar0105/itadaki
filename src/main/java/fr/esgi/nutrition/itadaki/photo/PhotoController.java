package fr.esgi.nutrition.itadaki.photo;

import fr.esgi.nutrition.itadaki.user.User;
import fr.esgi.nutrition.itadaki.user.UserRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class PhotoController {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final MealPhotoRepository mealPhotoRepository;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    Authentication authentication) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Aucun fichier fourni");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().body("Format non supporté. Formats acceptés : JPG, PNG, WEBP, GIF");
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow();

        MealPhoto photo = MealPhoto.builder()
                .filename(file.getOriginalFilename())
                .contentType(contentType)
                .imageData(file.getBytes())
                .uploadedAt(LocalDateTime.now())
                .user(user)
                .build();

        MealPhoto saved = mealPhotoRepository.save(photo);

        return ResponseEntity.ok(new PhotoUploadResponse(
                saved.getId(),
                saved.getFilename(),
                saved.getUploadedAt()
        ));
    }

    @GetMapping("/history")
    public ResponseEntity<List<HistoryResponse>> history(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow();

        List<HistoryResponse> history = mealPhotoRepository
                .findByUserOrderByUploadedAtDesc(user)
                .stream()
                .map(p -> new HistoryResponse(
                        p.getId(),
                        p.getFilename(),
                        p.getUploadedAt(),
                        p.getAnalysisResult(),
                        p.getCalories()
                ))
                .toList();

        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow();

        return mealPhotoRepository.findById(id)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.getContentType()))
                        .body(p.getImageData()))
                .orElse(ResponseEntity.notFound().build());
    }
}
