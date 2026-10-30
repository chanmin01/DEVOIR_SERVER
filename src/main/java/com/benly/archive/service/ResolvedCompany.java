package com.benly.archive.service;

import com.benly.archive.entity.Company;

public record ResolvedCompany(Company company, boolean created) {
}
