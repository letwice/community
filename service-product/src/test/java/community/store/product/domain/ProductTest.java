package community.store.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import community.store.common.web.ApiException;

class ProductTest {

    @Test
    void reservingLastItemMarksSoldOutAndReleaseReactivates() {
        Product product = new Product("vendor", "Lamp", null, new BigDecimal("100.00"), ProductCondition.NEW, "Electronics", 2);

        product.reserve(2);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
        assertThat(product.getStockQuantity()).isZero();

        product.release(1);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void cannotReserveMoreThanStock() {
        Product product = new Product("vendor", "Lamp", null, new BigDecimal("100.00"), ProductCondition.NEW, "Electronics", 1);

        assertThatThrownBy(() -> product.reserve(2)).isInstanceOf(ApiException.class).hasMessageContaining("Only 1 in stock");
    }

    @Test
    void cannotBuyRemovedProduct() {
        Product product = new Product("vendor", "Lamp", null, new BigDecimal("100.00"), ProductCondition.NEW, "Electronics", 5);
        product.remove();

        assertThatThrownBy(() -> product.reserve(1)).isInstanceOf(ApiException.class);
    }
}
