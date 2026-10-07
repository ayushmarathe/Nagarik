package com.nagarik.web;

import com.nagarik.domain.Category;
import com.nagarik.domain.IssueStatus;
import com.nagarik.web.dto.OptionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * The frontend builds its filters and pickers from these, so adding a category
 * to the enum is enough to make it appear in the interface.
 */
@RestController
@RequestMapping("/api")
public class MetaController {

    @GetMapping("/categories")
    public List<OptionResponse> categories() {
        return Arrays.stream(Category.values())
                .map(category -> new OptionResponse(category.name(), category.getLabel()))
                .toList();
    }

    @GetMapping("/statuses")
    public List<OptionResponse> statuses() {
        return Arrays.stream(IssueStatus.values())
                .map(status -> new OptionResponse(status.name(), status.getLabel()))
                .toList();
    }
}
