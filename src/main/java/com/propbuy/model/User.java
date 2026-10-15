package com.propbuy.model;
import jakarta.persistence.*; import lombok.*; import java.time.*; import com.fasterxml.jackson.annotation.JsonIgnore;
@Entity @Table(name="users") @Getter @Setter @NoArgsConstructor
public class User { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(nullable=false) private String name; @Column(nullable=false,unique=true) private String email; @JsonIgnore @Column(name="password_hash",nullable=false) private String passwordHash; @Enumerated(EnumType.STRING) private Role role; private boolean verified; @Column(name="created_at") private LocalDateTime createdAt=LocalDateTime.now(); public enum Role { SELLER,BUYER,ADMIN } }
