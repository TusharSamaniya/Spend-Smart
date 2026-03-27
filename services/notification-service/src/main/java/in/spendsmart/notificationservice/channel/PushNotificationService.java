package in.spendsmart.notificationservice.channel;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PushNotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(PushNotificationService.class);

    public void sendPush(String deviceToken, String title, String body) {
        try {
            Message message = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            FirebaseMessaging.getInstance().send(message);
        } catch (Exception ex) {
            LOG.error("Failed to send push notification to device token", ex);
        }
    }

    public void sendPushToTopic(String topic, String title, String body) {
        try {
            Message message = Message.builder()
                    .setTopic(topic)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            FirebaseMessaging.getInstance().send(message);
        } catch (Exception ex) {
            LOG.error("Failed to send push notification to topic: {}", topic, ex);
        }
    }
}
