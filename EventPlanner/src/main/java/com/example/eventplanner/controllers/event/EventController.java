package com.example.eventplanner.controllers.event;

import com.example.eventplanner.dto.event.EventOverviewDTO;
import com.example.eventplanner.dto.filter.EventFiltersDTO;
import com.example.eventplanner.services.event.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {
    private final EventService eventService;
    @GetMapping("/top")
    public ResponseEntity<Page<EventOverviewDTO>> getTopEvents(
            @PageableDefault(size = 5, sort = "date", direction = Sort.Direction.DESC) Pageable eventPage
    ) {
        return ResponseEntity.ok(eventService.getTop(eventPage));
    }
    @GetMapping("/all")
    public ResponseEntity<Page<EventOverviewDTO>> getAllEvents(
            @PageableDefault(size = 10, sort = "date", direction = Sort.Direction.DESC) Pageable eventPage
    ) {
        return ResponseEntity.ok(eventService.getAll(eventPage));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<EventOverviewDTO>> filterEvents(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10) Pageable pageable) {
        EventFiltersDTO eventFiltersDTO=new EventFiltersDTO(startDate,endDate,type,city);
        return ResponseEntity.ok(eventService.search(eventFiltersDTO,search,pageable));
    }
}
