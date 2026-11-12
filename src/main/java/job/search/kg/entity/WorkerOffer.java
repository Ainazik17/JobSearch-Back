package job.search.kg.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** «Мои услуги» (DESIGN_SPEC 6.4b): исполнитель готов к подработке. Одна карточка на пользователя. */
@Entity
@Table(name = "worker_offers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkerOffer {

    public static final String SKILL_SEPARATOR = "|";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    /** Профессии через «|» */
    @Column(name = "skills", nullable = false, length = 1000)
    private String skills;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_unit", nullable = false, length = 20)
    private PriceUnit priceUnit = PriceUnit.HOUR;

    /** Дни через запятую: «Пн,Вт,Сб» */
    @Column(name = "days", nullable = false, length = 40)
    private String days;

    @Column(name = "district")
    private String district;

    @Column(name = "about", columnDefinition = "TEXT")
    private String about;

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

    public enum PriceUnit { HOUR, VISIT, SHIFT }
}
