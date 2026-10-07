package com.nagarik.repo;

import com.nagarik.domain.Category;

/**
 * How many reports sit under each category. A projection interface rather than
 * a constructor expression, so the query stays plain JPQL.
 */
public interface CategoryTally {

    Category getCategory();

    long getTotal();
}
