package dev.baliak.beerclient.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;


/**
 * Generic DTO representing a paginated response returned by a remote API.
 *
 * <p>This record mirrors the structure of a typical Spring Data page response:
 * it contains a list of items (content) and pagination metadata (page).</p>
 *
 * <p>Designed to:
 * <ul>
 *   <li>Deserialize JSON from downstream services</li>
 *   <li>Be resilient to schema changes (unknown fields ignored)</li>
 *   <li>Provide convenient conversion to Spring's {@link Page}</li>
 * </ul>
 *
 * @param <T>     type of elements inside the page
 * @param content list of items in the current page
 * @param page    pagination metadata (may be null in fallback scenarios)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PagedResponse<T>(
        List<T> content,
        PageMetadata page
) {
    /**
     * Compact canonical constructor.
     *
     * <p>Ensures null-safety for content to avoid NullPointerExceptions
     * when iterating or converting to {@link Page}.</p>
     *
     * <p>If the remote API returns null content, it is replaced with an empty list.</p>
     */
    public PagedResponse {
        content = content != null ? content : List.of();
    }

    /**
     * Factory method for fallback scenarios.
     *
     * <p>Used when the downstream service is unavailable but the caller
     * still expects a valid object structure (e.g., UI rendering).</p>
     *
     * <p>Returns:
     * <ul>
     *   <li>empty content</li>
     *   <li>null page metadata (indicating no real pagination info)</li>
     * </ul>
     */
    public static <T> PagedResponse<T> empty() {
        return new PagedResponse<>(List.of(), null);
    }

    /**
     * Converts this DTO into a Spring Data {@link Page}.
     *
     * <p>This is useful when your controller/service layer expects
     * a {@link Page} abstraction for further processing.</p>
     *
     * <p>If page metadata is missing (e.g., fallback case),
     * total elements default to content size.</p>
     *
     * @param pageable pagination information (page number, size, sort)
     * @return Spring {@link Page} instance
     */
    public Page<T> toPage(Pageable pageable) {
        long total = page != null ? page.totalElements() : content.size();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Nested record representing pagination metadata.
     *
     * <p>This structure aligns with Spring Data's Page JSON format:</p>
     * <ul>
     *   <li>size – size of the page</li>
     *   <li>totalElements – total number of items across all pages</li>
     *   <li>totalPages – total number of pages</li>
     *   <li>number – current page index (0-based)</li>
     * </ul>
     *
     * <p>Unknown properties are ignored to allow backward/forward compatibility.</p>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageMetadata(
            int size,
            long totalElements,
            int totalPages,
            int number
    ) {
    }
}