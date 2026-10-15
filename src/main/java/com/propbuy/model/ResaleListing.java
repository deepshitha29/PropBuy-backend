package com.propbuy.model;
import jakarta.persistence.*; import lombok.*; import java.math.*; import java.time.*;
@Entity @Table(name="resale_listings") @Getter @Setter @NoArgsConstructor public class ResaleListing { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @OneToOne(optional=false) @JoinColumn(name="property_id",unique=true) private Property property; @Column(name="asking_price") private BigDecimal askingPrice; private String status="PUBLISHED"; @Column(name="published_at") private LocalDateTime publishedAt=LocalDateTime.now(); }
