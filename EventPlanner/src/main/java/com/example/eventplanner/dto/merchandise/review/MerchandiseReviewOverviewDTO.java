package com.example.eventplanner.dto.merchandise.review;

import lombok.Data;

@Data
public class MerchandiseReviewOverviewDTO {
    private String reviewersUsername;
    private String comment;
    private int rating;
}
