package com.propbuy.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "properties")
@Getter
@Setter
@NoArgsConstructor
public class Property {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "seller_id")
    private User seller;
    private String title, description, propertyType, city, locality, address, postalCode,
            furnishing, amenities, workflowStatus, imageUrl;
    private BigDecimal area, expectedPrice, estimatedLow, estimatedHigh;
    private Integer bedrooms, bathrooms, propertyAge;
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
