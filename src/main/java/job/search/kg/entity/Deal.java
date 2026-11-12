package job.search.kg.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Сделка (DESIGN_SPEC 7): исполнитель и работодатель подтверждают, что работали вместе.
 *
 * ASK_WORKER     — работодатель пригласил по отклику; через 2 дня спрашиваем исполнителя «Работали?»
 * AWAIT_EMPLOYER — исполнитель ответил «да» (оценкой); ждём «Всё верно?» от работодателя, авто через 48 ч
 * AWAIT_WORKER   — работодатель отметил найм («Кого наняли?»); ждём исполнителя, авто через 48 ч
 * CONFIRMED / DISPUTED (работодатель оспорил) / PROOF_REVIEW (исполнитель прислал доказательство) / DECLINED
 */
@Entity
@Table(name = "deals")
@Data
@NoArgsConstructor
public class Deal {

    public enum Status { ASK_WORKER, AWAIT_WORKER, AWAIT_EMPLOYER, CONFIRMED, DISPUTED, PROOF_REVIEW, DECLINED }

    public enum Source { INVITE, HIRE, FOUND }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private User worker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id", nullable = false)
    private User employer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vacancy_id")
    private Vacancy vacancy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gig_id")
    private Gig gig;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "ask_after")
    private LocalDateTime askAfter;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "points_awarded", nullable = false)
    private Boolean pointsAwarded = false;

    @Column(name = "worker_stars") private Short workerStars;
    @Column(name = "worker_paid") private Short workerPaid;
    @Column(name = "worker_conditions") private Short workerConditions;
    @Column(name = "worker_attitude") private Short workerAttitude;
    @Column(name = "worker_text", columnDefinition = "TEXT") private String workerText;

    @Column(name = "employer_stars") private Short employerStars;
    @Column(name = "employer_punctual") private Short employerPunctual;
    @Column(name = "employer_quality") private Short employerQuality;
    @Column(name = "employer_communication") private Short employerCommunication;
    @Column(name = "employer_text", columnDefinition = "TEXT") private String employerText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
