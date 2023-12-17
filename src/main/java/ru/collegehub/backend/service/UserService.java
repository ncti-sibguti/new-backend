package ru.collegehub.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.collegehub.backend.api.request.AuthRequest;
import ru.collegehub.backend.api.request.ScheduleChangeRequest;
import ru.collegehub.backend.api.response.GroupResponse;
import ru.collegehub.backend.api.response.ScheduleResponse;
import ru.collegehub.backend.api.response.UserResponse;
import ru.collegehub.backend.model.Group;
import ru.collegehub.backend.model.Role;
import ru.collegehub.backend.model.Schedule;
import ru.collegehub.backend.model.Subject;
import ru.collegehub.backend.model.Template;
import ru.collegehub.backend.model.User;
import ru.collegehub.backend.repository.GroupRepository;
import ru.collegehub.backend.repository.ScheduleRepository;
import ru.collegehub.backend.repository.SubjectRepository;
import ru.collegehub.backend.repository.TemplateRepository;
import ru.collegehub.backend.repository.UserRepository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final GroupRepository groupRepository;
    private final TemplateRepository templateRepository;
    private final ScheduleRepository scheduleRepository;
    private final SubjectRepository subjectRepository;

    public Map<String, Set<ScheduleResponse>> getSchedule() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) auth.getPrincipal();

        for (Role role : user.getRoles()) {
            if (role.getName().equals("ROLE_STUDENT"))
                return getSchedule(user.getGroup().getId());
            else if (role.getName().equals("ROLE_TEACHER")) {
                return getScheduleFromTeacher(user);
            }
        }

        return Collections.emptyMap();
    }

    public String updateCredential(AuthRequest request) throws IllegalArgumentException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) auth.getPrincipal();

        if (request.getUsername() != null) {
            user.setEmail(request.getUsername());
        }
        if (request.getPassword() == null || request.getPassword().length() <= 5) {
            throw new IllegalArgumentException("Не удалось поменять пароль");
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
        return "Credential was updated";
    }

    @Transactional(readOnly = false)
    public String changeSchedule(ScheduleChangeRequest request) throws ParseException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        User teacher = (User) auth.getPrincipal();

        List<Group> groups = new ArrayList<>();
        for (String gr : request.getGroup()) {
            Group group = groupRepository.findByName(gr)
                    .orElseThrow(() -> {
                        log.error(String.format("Group %s not found", gr));
                        return new IllegalArgumentException(String.format("Group %s not found", gr));
                    });
            groups.add(group);
        }

        Subject subject = subjectRepository.findByName(request.getSubject())
                .orElseThrow(() -> {
                    log.error(String.format("Subject %s not found", request.getSubject()));
                    return new IllegalArgumentException(String.format("Subject %s not found", request.getSubject()));
                });

        SimpleDateFormat format = new SimpleDateFormat();
        format.applyPattern("yyyy-MM-dd");

        for (Group group : groups) {
            Schedule schedule = Schedule.builder()
                    .date(LocalDate.parse(request.getDate()))
                    .group(group)
                    .teacher(teacher)
                    .numberPair(request.getNumberPair())
                    .subject(subject)
                    .classroom(request.getClassroom())
                    .build();
            // todo: kafka send
//            rabbitTemplate.convertAndSend(UPDATE_CLASSROOM,
//                    new HashMap<>() {{
//                        put("date", request.getDate());
//                        put("group", schedule.getGroup().getName());
//                        put("pair", schedule.getNumberPair());
//                        put("classroom", schedule.getClassroom());
//                    }});
            scheduleRepository.save(schedule);
        }
        return "Changes was added";
    }

    public List<GroupResponse> getGroups() {
        List<Group> groups = groupRepository.findAll();

        List<GroupResponse> dtos = new ArrayList<>(groups.size());

        groups.forEach(group -> dtos
                .add(GroupResponse.builder()
                        .id(group.getId())
                        .name(group.getName())
                        .build()));

        return dtos;
    }

    public Map<String, Set<ScheduleResponse>> getSchedule(Long id) {
        Group group = groupRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Group not found"));
        Map<String, Set<ScheduleResponse>> map = new HashMap<>();

        Set<ScheduleResponse> currSample = getTypeSchedule(group);

        for (ScheduleResponse s : currSample) {
            map.computeIfAbsent(s.getDay(), k -> new HashSet<>()).add(s);
        }
        List<Schedule> sch = scheduleRepository.findLatestScheduleForGroup(group.getId());

        if (!sch.isEmpty()) {
            for (Schedule schedule : sch) {
                String dayInWeek = LocalDate
                        .parse(sch.get(0).getDate().toString(), DateTimeFormatter.ISO_DATE)
                        .getDayOfWeek()
                        .getDisplayName(TextStyle.FULL, new Locale("ru"));
                String capitalizedDay = dayInWeek.substring(0, 1).toUpperCase() + dayInWeek.substring(1);

                Set<ScheduleResponse> set = map.computeIfAbsent(capitalizedDay, k -> new HashSet<>());

                ScheduleResponse scheduleResponse = ScheduleResponse.builder()
                        .day(capitalizedDay)
                        .numberPair(schedule.getNumberPair())
                        .subject(schedule.getSubject().getName())
                        .data(List.of(format("%s %s %s",
                                schedule.getTeacher().getLastname(),
                                schedule.getTeacher().getFirstname(),
                                schedule.getTeacher().getSurname())))
                        .classroom(schedule.getClassroom())
                        .build();
                set.removeIf(s -> Objects.equals(s.getNumberPair(), scheduleResponse.getNumberPair()));
                set.add(scheduleResponse);

            }
        }

        sortedMap(map);

        return map;
    }

    public UserResponse getUserById(Long id) {
        User candidate = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return UserResponse.builder()
                .id(candidate.getId())
                .firstname(candidate.getFirstname())
                .lastname(candidate.getLastname())
                .surname(candidate.getSurname())
                .email(candidate.getUsername())
                .role(candidate.getRoles())
                .build();
    }

    public List<UserResponse> getUsersWithPagination(int page, int size) {
        // Создаем объект PageRequest с указанием номера страницы и размера страницы
        Pageable pageable = PageRequest.of(page, size);

        // Получаем текущего пользователя из контекста безопасности
        var auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();

        // Выполняем запрос к репозиторию с использованием пагинации
        Page<User> usersPage = userRepository.findAllByOrderByLastname(pageable);

        // Фильтруем пользователей, исключая текущего пользователя (ваш аккаунт)

        List<UserResponse> users = new ArrayList<>();

        usersPage.forEach(user -> {
            if (!user.getId().equals(currentUser.getId())) {
                users.add(UserResponse.builder()
                        .id(user.getId())
                        .firstname(user.getFirstname())
                        .lastname(user.getLastname())
                        .surname(user.getSurname())
                        .email(user.getEmail())
                        .role(user.getRoles())
                        .build());
            }
        });

        return users;
    }

    private Map<String, Set<ScheduleResponse>> getScheduleFromTeacher(User user) {
        Map<String, Set<ScheduleResponse>> map = new HashMap<>();
        List<Schedule> sch = scheduleRepository.findLatestScheduleForTeacher(user.getId());
        List<Template> list = templateRepository.findAllByTeacher(user);

        for (Template template : getTypeSchedule(list)) {
            String key = template.getDay();
            ScheduleResponse dto = ScheduleResponse.builder()
                    .day(key)
                    .classroom(template.getClassroom())
                    .data(List.of(template.getGroup().getName()))
                    .numberPair(template.getNumberPair())
                    .subject(template.getSubject().getName())
                    .build();

            Optional<ScheduleResponse> found = map.getOrDefault(key, Collections.emptySet())
                    .stream()
                    .filter(scheduleDTO ->
                            scheduleDTO.getNumberPair().equals(dto.getNumberPair()) &&
                            scheduleDTO.getSubject().equals(dto.getSubject()) &&
                            scheduleDTO.getClassroom().equals(dto.getClassroom())
                    )
                    .findFirst();

            if (found.isPresent()) {
                ScheduleResponse existing = found.get();
                Set<String> groups = new HashSet<>(existing.getData());
                groups.addAll(dto.getData());
                existing.setData(new ArrayList<>(groups));
            } else {
                map.computeIfAbsent(key, k -> new HashSet<>()).add(dto);
            }
        }

        for (Schedule schedule : sch) {
            String date = LocalDate.parse(schedule.getDate().toString(), DateTimeFormatter.ISO_DATE)
                    .getDayOfWeek()
                    .getDisplayName(TextStyle.FULL, new Locale("ru"));
            String capitalizedDay = date.substring(0, 1).toUpperCase() + date.substring(1);

            Set<ScheduleResponse> set = map.get(capitalizedDay);
            if (set != null) {
                ScheduleResponse scheduleResponse = ScheduleResponse.builder()
                        .numberPair(schedule.getNumberPair())
                        .subject(schedule.getSubject().getName())
                        .classroom(schedule.getClassroom())
                        .data(List.of(schedule.getGroup().getName()))
                        .build();
                set.removeIf(s -> Objects.equals(s.getNumberPair(), scheduleResponse.getNumberPair()));
                set.add(scheduleResponse);
            }
        }

        sortedMap(map);
        return map;
    }

    private void sortedMap(Map<String, Set<ScheduleResponse>> map) {
        map.forEach((key, value) -> {
            Set<ScheduleResponse> sortedSet = value.stream()
                    .sorted(Comparator.comparingInt(ScheduleResponse::getNumberPair))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            map.put(key, sortedSet);
        });
    }

    private Set<Template> getTypeSchedule(List<Template> list) {
        String currentWeekType = getCurrentWeekType();
        return list.stream()
                .filter(s -> s.getParity().equals("0") || s.getParity().equals(currentWeekType))
                .collect(Collectors.toSet());
    }

    private String getCurrentWeekType() {
        LocalDate currentDate = LocalDate.now();
        int currentWeekNumber = currentDate.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear());
        return currentWeekNumber % 2 == 0 ? "2" : "1";
    }

    private Set<ScheduleResponse> getTypeSchedule(Group group) {
        List<Template> template = templateRepository.findAllByGroup(group);
        String currentWeekType = getCurrentWeekType();
        Map<String, ScheduleResponse> mergedScheduleMap = new HashMap<>();

        template.stream()
                .filter(s -> s.getParity().equals("0") || s.getParity().equals(currentWeekType))
                .forEach(s -> {
                    ScheduleResponse dto = convert(s);
                    String key = dto.getDay() + "-" + dto.getNumberPair() + "-" + dto.getSubject();
                    ScheduleResponse mergedDto = mergedScheduleMap.get(key);

                    if (mergedDto != null) {
                        mergedDto.getData().addAll(dto.getData());
                        mergedDto.setClassroom(mergedDto.getClassroom() + "/" + dto.getClassroom());
                    } else {
                        mergedDto = new ScheduleResponse();
                        mergedDto.setDay(dto.getDay());
                        mergedDto.setNumberPair(dto.getNumberPair());
                        mergedDto.setSubject(dto.getSubject());
                        mergedDto.setData(new ArrayList<>(dto.getData()));
                        mergedDto.setClassroom(dto.getClassroom());
                        mergedScheduleMap.put(key, mergedDto);
                    }
                });

        return new HashSet<>(mergedScheduleMap.values());
    }

    private ScheduleResponse convert(Template template) {
        return ScheduleResponse.builder()
                .day(template.getDay())
                .numberPair(template.getNumberPair())
                .subject(template.getSubject().getName())
                .data(List.of(format("%s %s %s",
                        template.getTeacher().getLastname(),
                        template.getTeacher().getFirstname(),
                        template.getTeacher().getSurname())))
                .classroom(template.getClassroom())
                .build();
    }

}
