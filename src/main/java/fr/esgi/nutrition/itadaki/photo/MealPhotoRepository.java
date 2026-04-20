package fr.esgi.nutrition.itadaki.photo;

import fr.esgi.nutrition.itadaki.user.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealPhotoRepository extends JpaRepository<MealPhoto, Long> {

    List<MealPhoto> findByUserOrderByUploadedAtDesc(User user);
}
