package community.store.product.web;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import community.store.common.security.CurrentUser;
import community.store.common.web.PageResponse;
import community.store.product.domain.ProductCondition;
import community.store.product.dto.CreateProductRequest;
import community.store.product.dto.CreateReviewRequest;
import community.store.product.dto.ProductDetailResponse;
import community.store.product.dto.ProductResponse;
import community.store.product.dto.ReviewResponse;
import community.store.product.dto.UpdateProductRequest;
import community.store.product.service.ProductService;
import community.store.product.service.ReviewService;

@RestController
@RequestMapping("/frontend/products")
public class ProductController {

    private final ProductService productService;
    private final ReviewService reviewService;

    public ProductController(ProductService productService, ReviewService reviewService) {
        this.productService = productService;
        this.reviewService = reviewService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('VENDOR', 'FACULTY')")
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(CurrentUser.guid(), request);
    }

    @PutMapping("/{guid}")
    @PreAuthorize("hasAnyRole('VENDOR', 'FACULTY')")
    public ProductResponse update(@PathVariable String guid, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(CurrentUser.guid(), guid, request);
    }

    @DeleteMapping("/{guid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('VENDOR', 'FACULTY')")
    public void delete(@PathVariable String guid) {
        productService.remove(CurrentUser.guid(), guid);
    }

    /**
     * Storefront browse ({@code search} is a title starts-with match). {@code mine=true} lists the caller's own
     * listings in every status except REMOVED (vendor/faculty dashboards).
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<ProductResponse> list(@RequestParam(required = false) String category,
                                              @RequestParam(required = false) ProductCondition condition,
                                              @RequestParam(required = false) String search,
                                              @RequestParam(defaultValue = "false") boolean mine,
                                              @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (mine) {
            return productService.listMine(CurrentUser.guid(), pageable);
        }
        return productService.search(category, condition, search, pageable);
    }

    @GetMapping("/{guid}")
    @PreAuthorize("isAuthenticated()")
    public ProductDetailResponse detail(@PathVariable String guid) {
        return productService.detail(guid);
    }

    @PostMapping("/{guid}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('STUDENT', 'VENDOR', 'COMMUNITY_LEADER', 'FACULTY')")
    public ReviewResponse review(@PathVariable String guid, @Valid @RequestBody CreateReviewRequest request) {
        return reviewService.createProductReview(CurrentUser.guid(), guid, request);
    }

    @GetMapping("/{guid}/reviews")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<ReviewResponse> reviews(@PathVariable String guid,
                                                @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return reviewService.listProductReviews(guid, pageable);
    }
}
