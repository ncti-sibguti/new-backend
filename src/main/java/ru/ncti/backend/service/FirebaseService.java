package ru.ncti.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ncti.backend.dto.ChatViewDTO;
import ru.ncti.backend.entity.Chat;
import ru.ncti.backend.entity.Group;
import ru.ncti.backend.entity.PrivateChat;
import ru.ncti.backend.entity.User;
import ru.ncti.backend.repository.ChatRepository;
import ru.ncti.backend.repository.GroupRepository;
import ru.ncti.backend.repository.PrivateChatRepository;
import ru.ncti.backend.repository.UserRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static ru.ncti.backend.model.RabbitQueue.PRIVATE_CHAT_NOTIFICATION;
import static ru.ncti.backend.model.RabbitQueue.PUBLIC_CHAT_NOTIFICATION;
import static ru.ncti.backend.model.RabbitQueue.UPDATE_SCHEDULE;

/**
 * user: ichuvilin
 */
@Service
@Log4j
@RequiredArgsConstructor
public class FirebaseService {

    // TODO: change all

    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final PrivateChatRepository privateChatRepository;
    private final RedisService redisService;
    private final FirebaseMessaging firebaseMessaging;
    private final ObjectMapper objectMapper;
    private final GroupRepository groupRepository;

    @RabbitListener(queues = PUBLIC_CHAT_NOTIFICATION)
    @Transactional(readOnly = true)
    public void sendPublicNotification(Map<String, String> map) throws FirebaseMessagingException, JsonProcessingException {
        Chat chat = chatRepository.findById(UUID.fromString(map.get("chat")))
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));
        User user = userRepository.findByEmail(map.get("user"))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Set<User> usersInChat = chat.getUsers();

        Set<String> onlineUserEmails = redisService.getValueSet(map.get("chat"));
        Set<User> userOnline = onlineUserEmails.stream()
                .map(userRepository::findByEmail)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        if (userOnline.size() != usersInChat.size()) {
            // Users offline
            Set<User> userOffline = new HashSet<>(usersInChat);
            userOffline.removeAll(userOnline);

            Set<String> fcmTokens = new HashSet<>();

            for (User userOff : userOffline) {
                fcmTokens.add(userOff.getDeviceId());
            }

            if (!fcmTokens.isEmpty()) {
                MulticastMessage multicastMessage = MulticastMessage.builder()
                        .addAllTokens(fcmTokens)
                        .setNotification(Notification.builder()
                                .setTitle(String.format("Чат: %s", chat.getName()))
                                .setBody(String.format("%s: %s", user.getFirstname(), map.get("text")))
                                .build())
                        .putData("page", "ChatRoute")
                        .putData("chat", objectMapper.writeValueAsString(ChatViewDTO.builder()
                                .type("PUBLIC")
                                .name(chat.getName())
                                .id(chat.getId())
                                .build()))
                        .build();
                firebaseMessaging.sendMulticast(multicastMessage);
            }
        }
    }

    @RabbitListener(queues = PRIVATE_CHAT_NOTIFICATION)
    @Transactional(readOnly = true)
    public void sendPrivateNotification(Map<String, String> map) throws FirebaseMessagingException, JsonProcessingException {
        PrivateChat chat = privateChatRepository.findById(UUID.fromString(map.get("chat")))
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));
        User user = userRepository.findByEmail(map.get("user"))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Set<User> usersInChat = Set.of(chat.getUser1(), chat.getUser2());

        Set<String> onlineUserEmails = redisService.getValueSet(map.get("chat"));

        if (onlineUserEmails.size() != 2) {
            Set<User> userOnline = onlineUserEmails.stream()
                    .map(userRepository::findByEmail)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());


            // User offline
            Set<User> userOffline = new HashSet<>(usersInChat);
            userOffline.removeAll(userOnline);

            Set<String> fcmTokens = new HashSet<>();

            for (User user1 : userOffline) {
                fcmTokens.add(user1.getDeviceId());
            }

            String name = String.format("%s %s", user.getFirstname(), user.getLastname());

            if (!fcmTokens.isEmpty()) {
                MulticastMessage multicastMessage = MulticastMessage.builder()
                        .addAllTokens(fcmTokens)
                        .setNotification(Notification.builder()
                                .setTitle(name)
                                .setBody(String.format("%s", map.get("text")))
                                .build())
                        .putData("page", "ChatRoute")
                        .putData("chat", objectMapper.writeValueAsString(ChatViewDTO.builder()
                                .type("PRIVATE")
                                .name(name)
                                .id(chat.getId())
                                .build()))
                        .build();
                firebaseMessaging.sendMulticast(multicastMessage);
            }
        }
    }

    @RabbitListener(queues = UPDATE_SCHEDULE)
    public void sendNotificationAboutChanges(Map<String, ?> map) throws FirebaseMessagingException {
        Group group = groupRepository.findByName(map.get("group").toString())
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        List<User> students = userRepository.findAllByGroupOrderByLastname(group);

        Set<String> tokens = new HashSet<>();

        students.forEach(student -> {
            Set<String> value = redisService.getValueSet(String.format("device:%s", student.getUsername()));
            if (value != null) {
                tokens.addAll(value);
            }
        });

        log.info(tokens);

        MulticastMessage multicastMessage = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(String.format("Преподаватель изменил кабинет на %s", map.get("date").toString().split(" ")[0]))
                        .setBody(String.format("№ пары %d : %s", map.get("pair"), map.get("classroom")))
                        .build())
                .build();
        firebaseMessaging.sendMulticast(multicastMessage);
    }

}
