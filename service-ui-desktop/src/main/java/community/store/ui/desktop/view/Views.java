package community.store.ui.desktop.view;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import community.store.ui.desktop.api.ApiClient;
import community.store.ui.desktop.api.ApiException;

public final class Views {

    private Views() {
    }

    /** True when the current user has banking details on file (the endpoint returns 404 otherwise). */
    public static boolean hasBankingDetails(ApiClient api) {
        try {
            api.get(api.userUrl + "/frontend/users/me/banking-details");
            return true;
        } catch (ApiException ex) {
            if (ex.getStatus() == 404) {
                return false;
            }
            throw ex;
        }
    }

    /** Extracts {@code content[]} from a {@code PageResponse}. */
    static List<JsonNode> content(JsonNode page) {
        List<JsonNode> items = new ArrayList<>();
        page.path("content").forEach(items::add);
        return items;
    }
}
