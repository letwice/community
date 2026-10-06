package community.store.product.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import community.store.common.web.ApiException;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guid", nullable = false, unique = true, length = 36, updatable = false)
    private String guid;

    @Column(name = "vendor_user_guid", nullable = false, length = 36, updatable = false)
    private String vendorUserGuid;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false)
    private ProductCondition condition;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected Product() {
    }

    public Product(String vendorUserGuid, String title, String description, BigDecimal price,
                   ProductCondition condition, String category, int stockQuantity) {
        this.guid = UUID.randomUUID().toString();
        this.vendorUserGuid = vendorUserGuid;
        this.title = title;
        this.description = description;
        this.price = price;
        this.condition = condition;
        this.category = category;
        this.stockQuantity = stockQuantity;
        this.status = stockQuantity > 0 ? ProductStatus.ACTIVE : ProductStatus.SOLD_OUT;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(String userGuid) {
        return vendorUserGuid.equals(userGuid);
    }

    public void update(String title, String description, BigDecimal price, ProductCondition condition,
                       String category, Integer stockQuantity, ProductStatus requestedStatus) {
        if (status == ProductStatus.REMOVED) {
            throw ApiException.conflict("Removed products cannot be edited");
        }
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (price != null) {
            this.price = price;
        }
        if (condition != null) {
            this.condition = condition;
        }
        if (category != null) {
            this.category = category;
        }
        if (stockQuantity != null) {
            this.stockQuantity = stockQuantity;
        }
        if (requestedStatus != null) {
            if (requestedStatus != ProductStatus.DRAFT && requestedStatus != ProductStatus.ACTIVE) {
                throw ApiException.badRequest("Status can only be set to DRAFT or ACTIVE");
            }
            this.status = requestedStatus;
        }
        syncStockStatus();
    }

    public void remove() {
        status = ProductStatus.REMOVED;
    }

    /** Reserves stock for a new order. */
    public void reserve(int quantity) {
        if (status != ProductStatus.ACTIVE) {
            throw ApiException.conflict("Product is not available for purchase");
        }
        if (quantity > stockQuantity) {
            throw ApiException.conflict("Only " + stockQuantity + " in stock");
        }
        stockQuantity -= quantity;
        syncStockStatus();
    }

    /** Returns stock from a cancelled order. */
    public void release(int quantity) {
        stockQuantity += quantity;
        syncStockStatus();
    }

    private void syncStockStatus() {
        if (status == ProductStatus.ACTIVE && stockQuantity <= 0) {
            status = ProductStatus.SOLD_OUT;
        } else if (status == ProductStatus.SOLD_OUT && stockQuantity > 0) {
            status = ProductStatus.ACTIVE;
        }
    }

    public Long getId() {
        return id;
    }

    public String getGuid() {
        return guid;
    }

    public String getVendorUserGuid() {
        return vendorUserGuid;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public ProductCondition getCondition() {
        return condition;
    }

    public String getCategory() {
        return category;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
