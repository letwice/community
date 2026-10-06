package community.store.product.web;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import community.store.common.security.CurrentUser;
import community.store.common.security.Roles;
import community.store.common.web.PageResponse;
import community.store.product.domain.EscrowStatus;
import community.store.product.dto.CreateOrderRequest;
import community.store.product.dto.OrderResponse;
import community.store.product.service.OrderService;

/**
 * Buyer/vendor ownership of each order is enforced in {@link OrderService}.
 */
@RestController
@RequestMapping("/frontend/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('STUDENT', 'VENDOR', 'COMMUNITY_LEADER', 'FACULTY')")
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(CurrentUser.guid(), request);
    }

    /** PoC stand-in for a PSP webhook: the buyer self-attests "I have paid via EFT" (SIMULATED). */
    @PostMapping("/{guid}/confirm-payment")
    @PreAuthorize("isAuthenticated()")
    public OrderResponse confirmPayment(@PathVariable String guid) {
        return orderService.confirmPayment(CurrentUser.guid(), guid);
    }

    @PostMapping("/{guid}/mark-shipped")
    @PreAuthorize("isAuthenticated()")
    public OrderResponse markShipped(@PathVariable String guid) {
        return orderService.markShipped(CurrentUser.guid(), guid);
    }

    @PostMapping("/{guid}/confirm-receipt")
    @PreAuthorize("isAuthenticated()")
    public OrderResponse confirmReceipt(@PathVariable String guid) {
        return orderService.confirmReceipt(CurrentUser.guid(), guid);
    }

    @PostMapping("/{guid}/cancel")
    @PreAuthorize("isAuthenticated()")
    public OrderResponse cancel(@PathVariable String guid) {
        return orderService.cancel(CurrentUser.guid(), guid);
    }

    @PostMapping("/{guid}/dispute")
    @PreAuthorize("isAuthenticated()")
    public OrderResponse dispute(@PathVariable String guid) {
        return orderService.dispute(CurrentUser.guid(), guid);
    }

    @GetMapping("/{guid}")
    @PreAuthorize("isAuthenticated()")
    public OrderResponse get(@PathVariable String guid) {
        return orderService.get(CurrentUser.guid(), CurrentUser.hasRole(Roles.ADMIN), guid);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<OrderResponse> list(@RequestParam(defaultValue = "buyer") String role,
                                            @RequestParam(required = false) EscrowStatus status,
                                            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.listMine(CurrentUser.guid(), role, status, pageable);
    }
}
