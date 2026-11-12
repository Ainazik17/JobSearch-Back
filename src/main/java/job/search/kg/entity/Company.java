package job.search.kg.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** Профиль компании-работодателя (DESIGN_SPEC 5). Одна на пользователя. */
@Entity
@Table(name = "companies")
@Data
@NoArgsConstructor
public class Company {

    public static final String PERK_SEPARATOR = "|";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 120)
    private String type;

    @Column(length = 60)
    private String sphere;

    @Column(length = 140)
    private String tagline;

    @Column(columnDefinition = "TEXT")
    private String about;

    @Column(length = 600)
    private String perks;

    @Column(length = 20)
    private String staff;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(nullable = false)
    private Boolean verified = false;

    @Column(name = "hired_count", nullable = false)
    private Integer hiredCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
