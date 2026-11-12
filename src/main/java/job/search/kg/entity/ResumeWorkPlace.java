package job.search.kg.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/** «Где работали» в резюме */
@Entity
@Table(name = "resume_work_places")
@Data
@NoArgsConstructor
public class ResumeWorkPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @Column(nullable = false, length = 120)
    private String place;

    @Column(length = 120)
    private String role;

    @Column(name = "period_text", length = 60)
    private String periodText;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;
}
