package community.store.product.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import community.store.common.client.dto.UserSummaryResponse;
import community.store.common.web.ApiException;
import community.store.common.web.PageResponse;
import community.store.product.domain.Product;
import community.store.product.domain.ProductCondition;
import community.store.product.domain.ProductImage;
import community.store.product.domain.ProductImageRepository;
import community.store.product.domain.ProductRepository;
import community.store.product.domain.ProductReviewRepository;
import community.store.product.domain.ProductStatus;
import community.store.product.dto.CreateProductRequest;
import community.store.product.dto.ProductDetailResponse;
import community.store.product.dto.ProductResponse;
import community.store.product.dto.UpdateProductRequest;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final int DETAIL_REVIEW_COUNT = 10;

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ProductReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final AccountChecks accountChecks;

    public ProductService(ProductRepository productRepository,
                          ProductImageRepository imageRepository,
                          ProductReviewRepository reviewRepository,
                          ReviewService reviewService,
                          AccountChecks accountChecks) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.reviewRepository = reviewRepository;
        this.reviewService = reviewService;
        this.accountChecks = accountChecks;
    }

    @Transactional
    public ProductResponse create(String vendorGuid, CreateProductRequest request) {
        accountChecks.requireVerifiedSeller(vendorGuid);
        Product product = productRepository.save(new Product(vendorGuid, request.title(), request.description(),
                request.price(), request.condition(), request.category(), request.stockQuantity()));
        saveImages(product.getGuid(), request.imageUrls());
        log.info("Vendor {} listed product {} ({})", vendorGuid, product.getGuid(), product.getTitle());
        return toResponse(product);
    }

    @Transactional
    public ProductResponse update(String vendorGuid, String productGuid, UpdateProductRequest request) {
        Product product = loadOwned(vendorGuid, productGuid);
        product.update(request.title(), request.description(), request.price(), request.condition(),
                request.category(), request.stockQuantity(), request.status());
        if (request.imageUrls() != null) {
            imageRepository.deleteByProductGuid(productGuid);
            saveImages(productGuid, request.imageUrls());
        }
        log.info("Product {} updated by vendor {} (status={})", productGuid, vendorGuid, product.getStatus());
        return toResponse(product);
    }

    @Transactional
    public void remove(String vendorGuid, String productGuid) {
        Product product = loadOwned(vendorGuid, productGuid);
        product.remove();
        log.info("Product {} removed by vendor {}", productGuid, vendorGuid);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String category, ProductCondition condition, String search, Pageable pageable) {
        String titlePrefix = search == null || search.isBlank() ? null : escapeLike(search.trim()) + "%";
        String categoryFilter = category == null || category.isBlank() ? null : category;
        return toPage(productRepository.searchActive(categoryFilter, condition, titlePrefix, pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> listMine(String vendorGuid, Pageable pageable) {
        return toPage(productRepository.findByVendorUserGuidAndStatusNot(vendorGuid, ProductStatus.REMOVED, pageable));
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse detail(String productGuid) {
        Product product = productRepository.findByGuid(productGuid)
                .filter(p -> p.getStatus() != ProductStatus.REMOVED)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        UserSummaryResponse vendor = accountChecks.lookup(product.getVendorUserGuid());
        return new ProductDetailResponse(
                toResponse(product),
                new ProductDetailResponse.SellerResponse(vendor.guid(), vendor.displayName(), vendor.isVerified()),
                reviewRepository.averageRating(productGuid),
                reviewRepository.countByProductGuid(productGuid),
                reviewService.listProductReviews(productGuid,
                        PageRequest.of(0, DETAIL_REVIEW_COUNT, Sort.by(Sort.Direction.DESC, "createdAt"))).content());
    }

    Product loadOwned(String vendorGuid, String productGuid) {
        Product product = productRepository.findByGuid(productGuid)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (!product.isOwnedBy(vendorGuid)) {
            throw ApiException.forbidden("You can only manage your own listings");
        }
        return product;
    }

    List<ProductResponse> toResponses(Collection<Product> products) {
        Map<String, List<String>> images = imageRepository
                .findByProductGuidInOrderBySortOrderAsc(products.stream().map(Product::getGuid).toList()).stream()
                .collect(Collectors.groupingBy(ProductImage::getProductGuid,
                        Collectors.mapping(ProductImage::getUrl, Collectors.toList())));
        return products.stream().map(p -> toResponse(p, images.getOrDefault(p.getGuid(), List.of()))).toList();
    }

    private PageResponse<ProductResponse> toPage(Page<Product> page) {
        List<ProductResponse> content = toResponses(page.getContent());
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private ProductResponse toResponse(Product product) {
        return toResponses(List.of(product)).get(0);
    }

    private static ProductResponse toResponse(Product p, List<String> imageUrls) {
        return new ProductResponse(p.getGuid(), p.getVendorUserGuid(), p.getTitle(), p.getDescription(), p.getPrice(),
                p.getCondition().name(), p.getCategory(), p.getStockQuantity(), p.getStatus().name(), imageUrls,
                p.getCreatedAt(), p.getUpdatedAt());
    }

    private void saveImages(String productGuid, List<String> imageUrls) {
        if (imageUrls == null) {
            return;
        }
        for (int i = 0; i < imageUrls.size(); i++) {
            imageRepository.save(new ProductImage(productGuid, imageUrls.get(i), i));
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
