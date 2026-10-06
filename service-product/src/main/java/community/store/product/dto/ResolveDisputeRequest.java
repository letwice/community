package community.store.product.dto;

import jakarta.validation.constraints.NotNull;

public record ResolveDisputeRequest(@NotNull Outcome outcome) {

    public enum Outcome {
        REFUND, RELEASE
    }
}
