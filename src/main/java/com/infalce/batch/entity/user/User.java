package com.infalce.batch.entity.user;

import com.infalce.batch.entity.global.SoftDeleteTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_provider_id",
                columnNames = "provider_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends SoftDeleteTimeEntity {

    @Id
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID id;

    private String name;

    @Column(name = "profile_image")
    private String profileImage;

    private String email;

    @Column(name = "provider_id", nullable = false, unique = true)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan")
    private Plan plan;
}
