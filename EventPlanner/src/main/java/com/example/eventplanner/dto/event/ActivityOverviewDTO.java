package com.example.eventplanner.dto.event;

import java.time.LocalTime;

import com.example.eventplanner.dto.common.AddressDTO;
import lombok.Data;
import lombok.NoArgsConstructor;



@Data
@NoArgsConstructor
public class ActivityOverviewDTO {
    private int id;
    private String title;
    private String description;
    private LocalTime startTime;
    private LocalTime endTime;
    private AddressDTO address;
}