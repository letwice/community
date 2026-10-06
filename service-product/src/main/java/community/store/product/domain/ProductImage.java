package community.store.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_image")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_guid", nullable = false, length = 36)
    private String productGuid;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ProductImage() {
    }

    public ProductImage(String productGuid, String url, int sortOrder) {
        this.productGuid = productGuid;
        this.url = url;
        this.sortOrder = sortOrder;
    }

    public String getProductGuid() {
        return productGuid;
    }

    public String getUrl() {
        return url;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
