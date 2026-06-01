package com.urlshortener.url_shortener.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PagedResponse<T> {
    List<T> content;
    int page;
    int pageSize;
    long totalElements;
    int totalPages;
    boolean last;
}
