package job.search.kg.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Разовое задание (DESIGN_SPEC 6.4): «нужен человек на пару часов или день». */
@Entity
@Table(name = "gigs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Gig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "time_text", length = 120)
    private String timeText;

    @Column(name = "pay", nullable = false)
    private Integer pay;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_unit", nullable = false, length = 20)
    private PayUnit payUnit = PayUnit.TOTAL;

    @Column(name = "people_count", nullable = false, length = 5)
    private String peopleCount = "1";

    @Column(name = "address", nullable = false)
    private String address;

    private Double latitude;
    private Double longitude;

    /** Голосовое к объявлению: ссылка в хранилище и длительность, сек */
    @Column(name = "voice_url", length = 500)
    private String voiceUrl;

    @Column(name = "voice_duration")
    private Integer voiceDuration;

    @Column(name = "urgent", nullable = false)
    private Boolean urgent = false;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum PayUnit { TOTAL, HOUR, SHIFT }
}
