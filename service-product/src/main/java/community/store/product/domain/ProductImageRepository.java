package community.store.product.domain;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductGuidInOrderBySortOrderAsc(Collection<String> productGuids);

    void deleteByProductGuid(String productGuid);
}
