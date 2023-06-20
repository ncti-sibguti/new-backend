package ru.ncti.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * user: ichuvilin
 */
@Getter
@Setter
@Entity
@Builder
@Table(name = "chat_message")
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "text", columnDefinition = "TEXT", nullable = false)
    private String text;

    @ManyToOne(targetEntity = User.class, cascade = {CascadeType.ALL})
    private User sender;

    @ManyToOne(targetEntity = Chat.class, cascade = {CascadeType.ALL}, fetch = FetchType.EAGER)
    private Chat chat;

    @Column(name = "createdAt")
    private Instant createdAt;
}
