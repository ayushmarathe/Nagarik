package com.nagarik.repo;

import com.nagarik.domain.IssueStatus;

/** How many reports sit at each stage. */
public interface StatusTally {

    IssueStatus getStatus();

    long getTotal();
}
