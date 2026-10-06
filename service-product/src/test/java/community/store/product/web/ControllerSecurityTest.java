package community.store.product.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import community.store.common.security.CommonSecurityAutoConfiguration;
import community.store.common.web.CommonWebAutoConfiguration;
import community.store.product.dto.ProductResponse;
import community.store.product.service.ActivityService;
import community.store.product.service.OrderService;
import community.store.product.service.ProductService;
import community.store.product.service.ReviewService;
import community.store.product.service.SalesReportService;

/**
 * RBAC guards from the TA's matrix, enforced by {@code @PreAuthorize} on the controllers.
 */
@WebMvcTest(controllers = {ProductController.class, OrderController.class, ActivityController.class,
        BackofficeController.class, SystemController.class})
@ImportAutoConfiguration({CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
class ControllerSecurityTest {

    private static final String PRODUCT_JSON = """
            {"title":"Lamp","price":100.00,"condition":"NEW","category":"Electronics","stockQuantity":1}
            """;
    private static final String ACTIVITY_JSON = """
            {"title":"Clean-up","activityDate":"2099-01-01T10:00:00","location":"Lawn"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private ReviewService reviewService;
    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private ActivityService activityService;
    @MockitoBean
    private SalesReportService salesReportService;

    @Test
    void anonymousCannotBrowse() throws Exception {
        mvc.perform(get("/frontend/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentCanBrowse() throws Exception {
        mvc.perform(get("/frontend/products").with(as("STUDENT"))).andExpect(status().isOk());
    }

    @Test
    void studentCannotListProducts() throws Exception {
        mvc.perform(post("/frontend/products").with(as("STUDENT")).contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void communityLeaderCannotListProducts() throws Exception {
        mvc.perform(post("/frontend/products").with(as("COMMUNITY_LEADER")).contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void vendorAndFacultyCanListProducts() throws Exception {
        when(productService.create(anyString(), any())).thenReturn(sampleProduct());
        for (String role : new String[]{"VENDOR", "FACULTY"}) {
            mvc.perform(post("/frontend/products").with(as(role)).contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void vendorCannotPostActivities() throws Exception {
        mvc.perform(post("/frontend/activities").with(as("VENDOR")).contentType(MediaType.APPLICATION_JSON).content(ACTIVITY_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void leaderAndFacultyCanPostActivities() throws Exception {
        for (String role : new String[]{"COMMUNITY_LEADER", "FACULTY"}) {
            mvc.perform(post("/frontend/activities").with(as(role)).contentType(MediaType.APPLICATION_JSON).content(ACTIVITY_JSON))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void adminCannotBuy() throws Exception {
        mvc.perform(post("/frontend/orders").with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productGuid\":\"p\",\"quantity\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminSeesBackoffice() throws Exception {
        mvc.perform(get("/backoffice/vendors/v/sales-summary").with(as("VENDOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/backoffice/vendors/v/sales-summary").with(as("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void onlyAdminCanReleaseFundsApproveEventsOrSeePlatformSales() throws Exception {
        for (String role : new String[]{"STUDENT", "VENDOR", "COMMUNITY_LEADER", "FACULTY"}) {
            mvc.perform(post("/backoffice/orders/o/release").with(as(role))).andExpect(status().isForbidden());
            mvc.perform(post("/backoffice/activities/a/approve").with(as(role))).andExpect(status().isForbidden());
            mvc.perform(get("/backoffice/sales-summary").with(as(role))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/backoffice/orders/o/release").with(as("ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/backoffice/activities/a/approve").with(as("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/backoffice/sales-summary").with(as("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void systemApiRequiresSystemRole() throws Exception {
        mvc.perform(get("/system/health").with(as("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/system/health").with(as("SYSTEM"))).andExpect(status().isOk());
    }

    @Test
    void invalidRequestReturnsBadRequest() throws Exception {
        mvc.perform(post("/frontend/products").with(as("VENDOR")).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject("user-" + role.toLowerCase())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static ProductResponse sampleProduct() {
        return new ProductResponse("g", "v", "Lamp", null, java.math.BigDecimal.TEN, "NEW", "Electronics", 1, "ACTIVE",
                java.util.List.of(), null, null);
    }
}
