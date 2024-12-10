package com.example.eventplanner.controllers.merchandise;

import com.example.eventplanner.dto.filter.ServiceFiltersDTO;
import com.example.eventplanner.dto.merchandise.MerchandiseOverviewDTO;
import com.example.eventplanner.dto.merchandise.review.ReviewMerchandiseRequestDTO;
import com.example.eventplanner.dto.merchandise.review.ReviewMerchandiseResponseDTO;
import com.example.eventplanner.dto.merchandise.service.*;
import com.example.eventplanner.dto.merchandise.service.create.CreateServiceRequestDTO;
import com.example.eventplanner.dto.merchandise.service.create.CreateServiceResponseDTO;
import com.example.eventplanner.dto.merchandise.service.update.UpdateServiceRequestDTO;
import com.example.eventplanner.services.merchandise.ServiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceController {
    private final ServiceService serviceService;


    @PostMapping("/{serviceId}/reserve")
    public ResponseEntity<ReservationResponseDTO> reserveService(
            @PathVariable int serviceId,
            @Valid @RequestBody ReservationRequestDTO request) {
        ReservationResponseDTO response = serviceService.reserveService(serviceId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping()
    public ResponseEntity<GetAllServicesResponseDTO> GetAll() {
        return ResponseEntity.ok(new GetAllServicesResponseDTO());
    }

    @GetMapping("sp/{id}")
    public ResponseEntity<List<CreateServiceResponseDTO>> GetAllBySpId(@PathVariable(value = "id") int id) {
        List<CreateServiceResponseDTO> responseDTO = serviceService.getAllBySpId(id);
        return ResponseEntity.ok(responseDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GetServiceByIdResponseDTO> GetById(@PathVariable(value = "id") int id) {
        return ResponseEntity.ok(new GetServiceByIdResponseDTO());
    }

    @GetMapping("/filter")
    public ResponseEntity<GetAllServicesResponseDTO> GetFiltered(
            @RequestParam (required = false) String category,
            @RequestParam (required = false) String eventType,
            @RequestParam (required = false) double maxPrice,
            @RequestParam (required = false) boolean available) {
        return ResponseEntity.ok(new GetAllServicesResponseDTO());
    }

    @GetMapping("/{title}")
    public ResponseEntity<GetServiceByIdResponseDTO> GetByTitle(@PathVariable(value = "title") String title) {
        return ResponseEntity.ok(new GetServiceByIdResponseDTO());
    }

    @PostMapping("/create")
    public ResponseEntity<CreateServiceResponseDTO> Create(@RequestBody CreateServiceRequestDTO request) {
        CreateServiceResponseDTO createServiceResponseDTO = serviceService.createService(request);
        return new ResponseEntity<>(createServiceResponseDTO, HttpStatus.CREATED);
    }

    @PutMapping("update/{id}")
    public ResponseEntity<CreateServiceResponseDTO> Update(@PathVariable int id, @RequestBody UpdateServiceRequestDTO request) {
        CreateServiceResponseDTO responseDTO = serviceService.updateService(id, request);
        return ResponseEntity.ok(responseDTO);
    }

    @PutMapping("delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        serviceService.deleteService(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ReviewMerchandiseResponseDTO> addReview(@RequestBody ReviewMerchandiseRequestDTO request, @PathVariable int id) {
        return ResponseEntity.ok(new ReviewMerchandiseResponseDTO());
    }

    @GetMapping("/search")
    public ResponseEntity<Page<MerchandiseOverviewDTO>> filterServices(
            @RequestParam(required = false) Double priceMin,
            @RequestParam(required = false) Double priceMax,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer durationMin,
            @RequestParam(required = false) Integer durationMax,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10) Pageable pageable) {

        ServiceFiltersDTO productFiltersDTO=new ServiceFiltersDTO(priceMin,priceMax,category,durationMin,durationMax,city);

        return ResponseEntity.ok(serviceService.search(productFiltersDTO,search,pageable));
    }

}
