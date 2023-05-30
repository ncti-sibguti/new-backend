package ru.ncti.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Ivan Chuvilin (@ichuvilin)
 * Date: 29-05-2023
 */
@Getter
@Setter
@Builder
public class TeacherScheduleViewDTO {
    private Integer numberPair;
    private String subject;
    private String classroom;
    private List<String> groups;
}
