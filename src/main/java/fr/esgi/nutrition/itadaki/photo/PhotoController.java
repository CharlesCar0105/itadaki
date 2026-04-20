package fr.esgi.nutrition.itadaki.photo;

import fr.esgi.nutrition.itadaki.user.User;
import fr.esgi.nutrition.itadaki.user.UserRepository;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public ResponseEntity<Object> upload(@RequestParam("file") MultipartFile file,
                                    Authentication authentication) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Aucun fichier fourni");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().body("Format non supporté. Formats acceptés : JPG, PNG, WEBP, GIF");
        }

        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();

        MealPhoto photo = MealPhoto.builder()
                .filename(file.getOriginalFilename())
                .contentType(contentType)
                .imageData(file.getBytes())
                .uploadedAt(LocalDateTime.now())
                .mealDate(LocalDate.now())
                .user(user)
                .build();

        MealPhoto saved = mealPhotoRepository.save(photo);
        return ResponseEntity.ok(new PhotoUploadResponse(saved.getId(), saved.getFilename(), saved.getUploadedAt()));
    }

    // Étape 1 — analyse par le premier LLM (à implémenter par l'équipe IA)
    @PostMapping("/{id}/analyze")
    public ResponseEntity<Object> analyze(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();

        return mealPhotoRepository.findById(id)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .map(p -> {
                    // Remplacer par l'appel Spring AI / LLM 1
                    p.setPreliminaryAnalysis("Analyse IA à venir");
                    p.setStatus(MealPhotoStatus.PRELIMINARY_DONE);
                    mealPhotoRepository.save(p);
                    return ResponseEntity.ok((Object) new AnalysisResponse(p.getId(), p.getPreliminaryAnalysis()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Étape 2 — analyse finale par le deuxième LLM avec le texte (potentiellement corrigé)
    @PostMapping("/{id}/finalize")
    public ResponseEntity<Object> finalizeAnalysis(@PathVariable Long id,
                                      @RequestBody FinalizeRequest req,
                                      Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();

        return mealPhotoRepository.findById(id)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .map(p -> {
                    // Sauvegarde du texte corrigé par l'utilisateur
                    p.setPreliminaryAnalysis(req.correctedAnalysis());
                    // Remplacer par l'appel Spring AI / LLM 2
                    p.setFinalAnalysis("Analyse finale IA à venir");
                    p.setCalories(500);
                    p.setStatus(MealPhotoStatus.FINALIZED);
                    mealPhotoRepository.save(p);
                    return ResponseEntity.ok((Object) toHistoryResponse(p));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history")
    public ResponseEntity<List<HistoryResponse>> history(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();

        List<HistoryResponse> history = mealPhotoRepository
                .findByUserOrderByUploadedAtDesc(user)
                .stream()
                .map(this::toHistoryResponse)
                .toList();

        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();

        return mealPhotoRepository.findById(id)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.getContentType()))
                        .body(p.getImageData()))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();

        return mealPhotoRepository.findById(id)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .<ResponseEntity<Void>>map(p -> {
                    mealPhotoRepository.delete(p);
                    return ResponseEntity.noContent().build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/meal-date")
    public ResponseEntity<Object> updateMealDate(@PathVariable Long id,
                                                  @RequestBody MealDateRequest req,
                                                  Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();

        return mealPhotoRepository.findById(id)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .map(p -> {
                    p.setMealDate(req.mealDate());
                    mealPhotoRepository.save(p);
                    return ResponseEntity.ok((Object) toHistoryResponse(p));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats/daily")
    public ResponseEntity<List<DailyCaloriesResponse>> dailyStats(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();

        List<DailyCaloriesResponse> result = mealPhotoRepository
                .findByUserOrderByUploadedAtDesc(user)
                .stream()
                .filter(p -> p.getStatus() == MealPhotoStatus.FINALIZED)
                .collect(Collectors.groupingBy(
                        p -> (p.getMealDate() != null ? p.getMealDate() : p.getUploadedAt().toLocalDate()).toString(),
                        Collectors.summingInt(p -> p.getCalories() != null ? p.getCalories() : 0)
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new DailyCaloriesResponse(e.getKey(), e.getValue()))
                .toList();

        return ResponseEntity.ok(result);
    }

    private HistoryResponse toHistoryResponse(MealPhoto p) {
        LocalDate mealDate = p.getMealDate() != null ? p.getMealDate() : p.getUploadedAt().toLocalDate();
        return new HistoryResponse(
                p.getId(),
                p.getFilename(),
                p.getUploadedAt(),
                mealDate,
                p.getStatus(),
                p.getPreliminaryAnalysis(),
                p.getFinalAnalysis(),
                p.getCalories()
        );
    }
}
