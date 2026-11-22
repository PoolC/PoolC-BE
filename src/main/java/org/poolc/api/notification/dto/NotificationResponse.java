package org.poolc.api.notification.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.poolc.api.notification.domain.Notification;
import org.poolc.api.notification.domain.NotificationType;

import java.time.LocalDateTime;

@Getter
@Setter(AccessLevel.PRIVATE)
public class NotificationResponse {
    private Long notificationId;
    private Boolean readStatus;
    private NotificationType notificationType;
    private LocalDateTime createdAt;
    private Long causedById;
    private String rewardName;
    private String rewardImageUrl;
    private String rewardBallType;
    private Integer rewardAmount;
    private Boolean rewardShiny;
    private String rewardReason;

    public static NotificationResponse of(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setNotificationId(notification.getId());
        response.setNotificationType(notification.getNotificationType());
        response.setReadStatus(notification.getReadStatus());
        response.setCreatedAt(notification.getCreatedAt());

        if (notification.getNotificationType() != NotificationType.BADGE) {
            response.setCausedById(notification.getCausedById());
        }

        response.setRewardName(notification.getRewardName());
        response.setRewardImageUrl(notification.getRewardImageUrl());
        response.setRewardBallType(notification.getRewardBallType());
        response.setRewardAmount(notification.getRewardAmount());
        response.setRewardShiny(notification.getRewardShiny());
        response.setRewardReason(notification.getRewardReason());

        return response;
    }
}
