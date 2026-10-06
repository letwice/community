package community.store.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the real Flyway migrations (schema + seed) against MySQL 8. Skipped when Docker is not available.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ProductRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderTransactionRepository orderRepository;

    @Test
    void seedDataIsLoaded() {
        assertThat(productRepository.count()).isEqualTo(20);
        assertThat(orderRepository.findByGuid("30000000-0000-0000-0000-000000000001"))
                .get().extracting(OrderTransaction::getEscrowStatus).isEqualTo(EscrowStatus.FUNDS_RELEASED);
    }

    @Test
    void titleSearchIsStartsWithNotContains() {
        assertThat(productRepository.searchActive(null, null, "Lab%", PageRequest.of(0, 50)).getContent())
                .extracting(Product::getTitle).containsExactly("Lab Coat (size M)");
        assertThat(productRepository.searchActive(null, null, "Coat%", PageRequest.of(0, 50)).getContent()).isEmpty();
    }

    @Test
    void persistsNewProduct() {
        Product saved = productRepository.saveAndFlush(new Product("vendor", "Kettle", null, new BigDecimal("199.99"),
                ProductCondition.NEW, "Appliances", 3));

        assertThat(productRepository.findByGuid(saved.getGuid())).isPresent();
    }

    @Autowired
    private ProductReviewRepository reviewRepository;
    @Autowired
    private ActivityReviewRepository activityReviewRepository;

    @Test
    void adminQueriesRunAgainstMysql() {
        String vendor = "00000000-0000-0000-0000-000000000003";
        assertThat(orderRepository.totalsByVendorAndStatus()).extracting(OrderTransactionRepository.VendorStatusTotal::getVendorGuid)
                .containsOnly(vendor);
        assertThat(orderRepository.searchAll(false, java.util.List.of(EscrowStatus.SHIPPED), vendor, PageRequest.of(0, 10)).getContent())
                .extracting(OrderTransaction::getEscrowReferenceNumber).containsExactly("ESC-000002");
        assertThat(orderRepository.searchAll(true, java.util.EnumSet.allOf(EscrowStatus.class), null, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(2);
        assertThat(productRepository.countByVendorForStatus(ProductStatus.ACTIVE)).hasSize(2);
        assertThat(reviewRepository.statsForVendor(vendor).getReviewCount()).isZero();
        assertThat(reviewRepository.findByVendor(vendor, PageRequest.of(0, 10)).getContent()).isEmpty();
        assertThat(activityReviewRepository.statsForOrganiser("00000000-0000-0000-0000-000000000005").average()).isEqualTo(5.0);
        assertThat(activityReviewRepository.statsForActivities(java.util.List.of("20000000-0000-0000-0000-000000000004")))
                .singleElement().extracting(GuidRatingStats::getReviewCount).isEqualTo(1L);
    }

    @Test
    void salesTotalsGroupByStatus() {
        assertThat(orderRepository.totalsByStatus("00000000-0000-0000-0000-000000000003"))
                .extracting(OrderTransactionRepository.StatusTotal::getStatus)
                .containsExactlyInAnyOrder(EscrowStatus.FUNDS_RELEASED, EscrowStatus.SHIPPED);
    }
}
