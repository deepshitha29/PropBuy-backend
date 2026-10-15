package com.propbuy.model;
import jakarta.persistence.*; import lombok.*; import java.math.*; import java.time.*;
@Entity @Table(name="acquisition_offers") @Getter @Setter @NoArgsConstructor public class AcquisitionOffer { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(optional=false) @JoinColumn(name="property_id") private Property property; @ManyToOne(optional=false) @JoinColumn(name="admin_id") private User admin; private BigDecimal amount; private String explanation,status="ISSUED"; @Column(name="valid_until") private LocalDate validUntil; }
