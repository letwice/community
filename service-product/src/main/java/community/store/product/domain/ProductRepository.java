package community.store.product.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByGuid(String guid);

    List<Product> findByGuidIn(Collection<String> guids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.guid = :guid")
    Optional<Product> findByGuidForUpdate(@Param("guid") String guid);

    /**
     * Storefront search. {@code titlePrefix} must already end in {@code %} (starts-with match, never contains).
     */
    @Query("""
            select p from Product p
            where p.status = community.store.product.domain.ProductStatus.ACTIVE
              and (:category is null or p.category = :category)
              and (:condition is null or p.condition = :condition)
              and (:titlePrefix is null or p.title like :titlePrefix)
            """)
    Page<Product> searchActive(@Param("category") String category, @Param("condition") ProductCondition condition,
                               @Param("titlePrefix") String titlePrefix, Pageable pageable);

    Page<Product> findByVendorUserGuidAndStatusNot(String vendorUserGuid, ProductStatus status, Pageable pageable);

    long countByVendorUserGuidAndStatus(String vendorUserGuid, ProductStatus status);

    @Query("select p.vendorUserGuid as guid, count(p) as total from Product p where p.status = :status group by p.vendorUserGuid")
    List<GuidCount> countByVendorForStatus(@Param("status") ProductStatus status);

    interface GuidCount {
        String getGuid();

        long getTotal();
    }
}
