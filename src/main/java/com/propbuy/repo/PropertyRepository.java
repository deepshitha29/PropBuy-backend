package com.propbuy.repo;

import com.propbuy.model.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface PropertyRepository extends JpaRepository<Property,Long>{
    List<Property> findByWorkflowStatus(String status);
    List<Property> findBySellerId(Long id);
    @Query("select p from Property p where (:status is null or p.workflowStatus = :status) and (:city is null or lower(p.city) like lower(concat('%', :city, '%'))) and (:propertyType is null or lower(p.propertyType) = lower(:propertyType)) order by p.createdAt desc")
    List<Property> searchAdmin(@Param("status") String status, @Param("city") String city, @Param("propertyType") String propertyType);
}
