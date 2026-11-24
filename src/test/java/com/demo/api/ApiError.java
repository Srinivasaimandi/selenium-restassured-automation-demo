package com.demo.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The body the API returns for errors. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiError(String message, String error) {}
