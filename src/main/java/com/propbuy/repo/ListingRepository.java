package com.propbuy.repo;

import com.propbuy.model.ResaleListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<ResaleListing, Long> {
    List<ResaleListing> findByStatus(String status);

    Optional<ResaleListing> findByPropertyId(Long id);

    @Query(value = """
            select l from ResaleListing l
            join fetch l.property p
            where l.status = 'PUBLISHED'
              and (:query is null or
                   lower(p.title) like lower(concat('%', :query, '%')) or
                   lower(p.description) like lower(concat('%', :query, '%')) or
                   lower(p.city) like lower(concat('%', :query, '%')) or
                   lower(p.locality) like lower(concat('%', :query, '%')))
              and (:city is null or lower(p.city) = lower(:city))
              and (:bedrooms is null or p.bedrooms = :bedrooms)
              and (:maxPrice is null or l.askingPrice <= :maxPrice)
              and (:propertyType is null or lower(p.propertyType) = lower(:propertyType))
            """, countQuery = """
            select count(l) from ResaleListing l
            join l.property p
            where l.status = 'PUBLISHED'
              and (:query is null or
                   lower(p.title) like lower(concat('%', :query, '%')) or
                   lower(p.description) like lower(concat('%', :query, '%')) or
                   lower(p.city) like lower(concat('%', :query, '%')) or
                   lower(p.locality) like lower(concat('%', :query, '%')))
              and (:city is null or lower(p.city) = lower(:city))
              and (:bedrooms is null or p.bedrooms = :bedrooms)
              and (:maxPrice is null or l.askingPrice <= :maxPrice)
              and (:propertyType is null or lower(p.propertyType) = lower(:propertyType))
            """)
    Page<ResaleListing> searchPublished(@Param("query") String query,
                                        @Param("city") String city,
                                        @Param("bedrooms") Integer bedrooms,
                                        @Param("maxPrice") java.math.BigDecimal maxPrice,
                                        @Param("propertyType") String propertyType,
                                        Pageable pageable);
}
