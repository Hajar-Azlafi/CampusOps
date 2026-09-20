package com.campusops.notification.repository;

import com.campusops.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUserIdAndLueFalseOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndLueFalse(Long userId);

    /**
     * Supprime les notifications d'un utilisateur. La colonne {@code user_id} est
     * NOT NULL : a la suppression d'un compte, ses notifications (personnelles et
     * sans valeur historique) sont supprimees plutot que detachees.
     */
    long deleteByUserId(Long userId);
}
