package fr.esgi.nutrition.itadaki.photo;

import fr.esgi.nutrition.itadaki.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "meal_photos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String contentType;

    @Lob
    @Column(nullable = false)
    private byte[] imageData;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MealPhotoStatus status = MealPhotoStatus.UPLOADED;

    // Résultat du premier LLM — modifiable par l'utilisateur
    @Column(columnDefinition = "TEXT")
    private String preliminaryAnalysis;

    // Résultat final du deuxième LLM
    @Column(columnDefinition = "TEXT")
    private String finalAnalysis;

    private Integer calories;

    private LocalDate mealDate;
}
