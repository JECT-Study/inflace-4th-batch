package com.infalce.batch.entity.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_need")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNeed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "need_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "need")
    private Need need;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
