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

public interface OrderTransactionRepository extends JpaRepository<OrderTransaction, Long> {

    Optional<OrderTransaction> findByGuid(String guid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderTransaction o where o.guid = :guid")
    Optional<OrderTransaction> findByGuidForUpdate(@Param("guid") String guid);

    @Query("""
            select o from OrderTransaction o
            where o.buyerUserGuid = :userGuid and (:status is null or o.escrowStatus = :status)
            """)
    Page<OrderTransaction> findForBuyer(@Param("userGuid") String userGuid, @Param("status") EscrowStatus status, Pageable pageable);

    @Query("""
            select o from OrderTransaction o
            where o.vendorUserGuid = :userGuid and (:status is null or o.escrowStatus = :status)
            """)
    Page<OrderTransaction> findForVendor(@Param("userGuid") String userGuid, @Param("status") EscrowStatus status, Pageable pageable);

    /** Admin order search; {@code anyStatus=true} ignores {@code statuses} (JPQL cannot bind an empty IN list). */
    @Query("""
            select o from OrderTransaction o
            where (:anyStatus = true or o.escrowStatus in :statuses)
              and (:vendorGuid is null or o.vendorUserGuid = :vendorGuid)
            """)
    Page<OrderTransaction> searchAll(@Param("anyStatus") boolean anyStatus, @Param("statuses") Collection<EscrowStatus> statuses,
                                     @Param("vendorGuid") String vendorGuid, Pageable pageable);

    long countByEscrowStatusIn(Collection<EscrowStatus> statuses);

    @Query("""
            select o.vendorUserGuid as vendorGuid, o.escrowStatus as status, count(o) as orderCount, sum(o.totalAmount) as totalAmount
            from OrderTransaction o group by o.vendorUserGuid, o.escrowStatus
            """)
    List<VendorStatusTotal> totalsByVendorAndStatus();

    interface VendorStatusTotal extends StatusTotal {
        String getVendorGuid();
    }

    @Query("""
            select o.escrowStatus as status, count(o) as orderCount, sum(o.totalAmount) as totalAmount
            from OrderTransaction o where o.vendorUserGuid = :vendorGuid group by o.escrowStatus
            """)
    List<StatusTotal> totalsByStatus(@Param("vendorGuid") String vendorGuid);

    interface StatusTotal {
        EscrowStatus getStatus();

        long getOrderCount();

        java.math.BigDecimal getTotalAmount();
    }
}
