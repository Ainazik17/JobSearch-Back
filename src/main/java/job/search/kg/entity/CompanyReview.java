package job.search.kg.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Отзыв сотрудника о компании (DESIGN_SPEC 5, вкладка «Отзывы»). */
@Entity
@Table(name = "company_reviews")
@Data
@NoArgsConstructor
public class CompanyReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id")
    private User author;

    @Column(name = "author_role", nullable = false, length = 80)
    private String authorRole;

    @Column(name = "tenure_text", length = 40)
    private String tenureText;

    @Column(nullable = false)
    private Short stars;

    @Column(name = "paid_on_time")
    private Short paidOnTime;

    private Short conditions;

    private Short attitude;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(nullable = false)
    private Boolean disputed = false;

    /** Сделка, из которой появился отзыв */
    @Column(name = "deal_id")
    private Long dealId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
