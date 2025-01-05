package com.example.eventplanner.services.merchandise;

import com.example.eventplanner.dto.common.AddressDTO;
import com.example.eventplanner.dto.filter.ProductFiltersDTO;
import com.example.eventplanner.dto.filter.ServiceFiltersDTO;
import com.example.eventplanner.dto.merchandise.MerchandiseOverviewDTO;

import com.example.eventplanner.dto.merchandise.MerchandisePhotoDTO;
import com.example.eventplanner.dto.merchandise.service.*;
import com.example.eventplanner.dto.merchandise.service.create.CreateServiceRequestDTO;
import com.example.eventplanner.dto.merchandise.service.create.CreateServiceResponseDTO;
import com.example.eventplanner.dto.merchandise.service.update.UpdateServiceRequestDTO;
import com.example.eventplanner.exceptions.ServiceReservationException;
import com.example.eventplanner.model.common.Address;
import com.example.eventplanner.model.common.Review;
import com.example.eventplanner.model.event.BudgetItem;
import com.example.eventplanner.model.event.Category;
import com.example.eventplanner.model.event.Event;
import com.example.eventplanner.model.event.EventType;
import com.example.eventplanner.model.merchandise.*;
import com.example.eventplanner.model.user.EventOrganizer;
import com.example.eventplanner.model.user.ServiceProvider;
import com.example.eventplanner.model.user.User;
import com.example.eventplanner.repositories.budget.BudgetItemRepository;
import com.example.eventplanner.repositories.budget.BudgetRepository;
import com.example.eventplanner.repositories.category.CategoryRepository;
import com.example.eventplanner.repositories.event.EventRepository;
import com.example.eventplanner.repositories.eventType.EventTypeRepository;
import com.example.eventplanner.repositories.merchandise.MerchandisePhotoRepository;
import com.example.eventplanner.repositories.merchandise.MerchandiseRepository;
import com.example.eventplanner.repositories.merchandise.ServiceRepository;
import com.example.eventplanner.repositories.merchandise.TimeslotRepository;
import com.example.eventplanner.repositories.user.ServiceProviderRepository;
import com.example.eventplanner.repositories.user.UserRepository;
import com.example.eventplanner.services.email.EmailService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceService {
    private final ServiceRepository serviceRepository;
    private final EventRepository eventRepository;
    private final TimeslotRepository timeslotRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final ServiceProviderRepository serviceProviderRepository;
    private final MerchandisePhotoRepository merchandisePhotoRepository;
    private final EventTypeRepository eventTypeRepository;
    private final CategoryRepository categoryRepository;
    private final MerchandiseRepository merchandiseRepository;
    private final BudgetItemRepository budgetItemRepository;
    private final BudgetRepository budgetRepository;

    public Page<MerchandiseOverviewDTO> search(int userId, ServiceFiltersDTO serviceFiltersDTO, String search, Pageable pageable) {
        // Fetch user details
        User currentUser = fetchUserDetails(userId);
        List<User> blockedUsers = currentUser != null ? currentUser.getBlockedUsers().stream().filter(u->u instanceof ServiceProvider).toList() : List.of();

        // Create a specification for filtering
        Specification<com.example.eventplanner.model.merchandise.Service> spec = createSpecification(serviceFiltersDTO, search)
                .and(excludeInvisible())
                .and(excludeBlockedProviders(currentUser, blockedUsers)); // Exclude services by blocked providers

        // Fetch paginated services with the composed specification
        Page<com.example.eventplanner.model.merchandise.Service> pagedServices = serviceRepository.findAll(spec, pageable);

        // Convert the results to DTOs
        return pagedServices.map(this::convertToOverviewDTO);
    }
    private Specification<com.example.eventplanner.model.merchandise.Service> excludeInvisible(){
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.isTrue(root.get("visible"));
        };
    }

    public List<CalendarTimeSlotDTO> getTimeslotsCalendar(int spId){
        List<CalendarTimeSlotDTO> calendarTimeSlotDTOs = new ArrayList<>();

        // Retrieve the service provider by ID
        ServiceProvider serviceProvider = serviceProviderRepository.findById(spId)
                .orElseThrow(() -> new RuntimeException("ServiceProvider not found"));

        // For each service offered by the service provider, retrieve the associated timeslots
        for (Merchandise merchandise : serviceProvider.getMerchandise()) {
            if (merchandise instanceof com.example.eventplanner.model.merchandise.Service) {
                com.example.eventplanner.model.merchandise.Service service = (com.example.eventplanner.model.merchandise.Service) merchandise;

                // Retrieve all timeslots for the service
                for (Timeslot timeslot : service.getTimeslots()) {
                    CalendarTimeSlotDTO calendarTimeSlotDTO = new CalendarTimeSlotDTO();
                    calendarTimeSlotDTO.setService(service.getTitle());
                    calendarTimeSlotDTO.setId(timeslot.getId());
                    calendarTimeSlotDTO.setStartTime(timeslot.getStartTime());
                    calendarTimeSlotDTO.setEndTime(timeslot.getEndTime());

                    // Add the DTO to the list
                    calendarTimeSlotDTOs.add(calendarTimeSlotDTO);
                }
            }
        }

        return calendarTimeSlotDTOs;
    }

    private Specification<com.example.eventplanner.model.merchandise.Service> excludeBlockedProviders(User currentUser, List<User> blockedUsers) {
        return (root, query, criteriaBuilder) -> {
            if (currentUser == null || blockedUsers == null || blockedUsers.isEmpty()||!(currentUser instanceof EventOrganizer)) {
                return criteriaBuilder.conjunction(); // No filter if there are no blocked users or no current user
            }

            // Create a subquery to find services of blocked service providers
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<ServiceProvider> serviceProviderRoot = subquery.from(ServiceProvider.class);

            // Join service provider's merchandise (services)
            Join<ServiceProvider, Merchandise> merchandiseJoin = serviceProviderRoot.join("merchandise");

            // Select IDs of services belonging to blocked service providers
            subquery.select(merchandiseJoin.get("id"))
                    .where(serviceProviderRoot.in(blockedUsers));

            // Exclude services that are in the subquery of blocked service providers
            return criteriaBuilder.not(root.get("id").in(subquery));
        };
    }

    private User fetchUserDetails(int userId) {
        return userRepository.findById(userId).orElse(null);
    }

    private Specification<com.example.eventplanner.model.merchandise.Service> createSpecification(ServiceFiltersDTO ServiceFiltersDTO, String search) {
        Specification<com.example.eventplanner.model.merchandise.Service> spec = Specification.where(null);
        spec = spec.and((root, query, criteriaBuilder) ->
                criteriaBuilder.isFalse(root.get("deleted"))
        );
        spec = addPriceRangeFilter(spec, ServiceFiltersDTO);
        spec = addCategoryFilter(spec, ServiceFiltersDTO);
        spec = addCityFilter(spec, ServiceFiltersDTO);
        spec = addGlobalSearch(spec, search);
        return spec;
    }

    private Specification<com.example.eventplanner.model.merchandise.Service> addPriceRangeFilter(Specification<com.example.eventplanner.model.merchandise.Service> spec, ServiceFiltersDTO serviceFiltersDTO) {
        return spec.and((root, query, criteriaBuilder) -> {
            if (serviceFiltersDTO.getPriceMin() == null && serviceFiltersDTO.getPriceMax() == null) {
                return null; // No price filter if both are null
            }

            if (serviceFiltersDTO.getPriceMin() != null && serviceFiltersDTO.getPriceMax() != null) {
                return criteriaBuilder.between(root.get("price"),
                        serviceFiltersDTO.getPriceMin(),
                        serviceFiltersDTO.getPriceMax());
            }

            if (serviceFiltersDTO.getPriceMin() != null) {
                return criteriaBuilder.greaterThanOrEqualTo(root.get("price"),
                        serviceFiltersDTO.getPriceMin());
            }

            // At this point, only priceMax is not null
            return criteriaBuilder.lessThanOrEqualTo(root.get("price"),
                    serviceFiltersDTO.getPriceMax());
        });
    }

    public List<ServiceOverviewDTO> getAll(){
        return serviceRepository.findAll().stream().map(this::convertToServiceOverviewDTO).toList();
    }

    public List<ServiceOverviewDTO> getAllByCategories(List<Integer> categories){
        return serviceRepository.findAllByCategories(categories).stream().map(this::convertToServiceOverviewDTO).toList();
    }


    private Specification<com.example.eventplanner.model.merchandise.Service> addCategoryFilter(
            Specification<com.example.eventplanner.model.merchandise.Service> spec,
            ServiceFiltersDTO ServiceFiltersDTO) {
        if (StringUtils.hasText(ServiceFiltersDTO.getCategory())) {
            return spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                            criteriaBuilder.lower(root.get("category")),
                            ServiceFiltersDTO.getCategory().toLowerCase()
                    )
            );
        }
        return spec;
    }

    private Specification<com.example.eventplanner.model.merchandise.Service> addCityFilter(Specification<com.example.eventplanner.model.merchandise.Service> spec, ServiceFiltersDTO ServiceFiltersDTO) {
        if (StringUtils.hasText(ServiceFiltersDTO.getCity())) {
            return spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                            criteriaBuilder.lower(root.get("address").get("city")),
                            ServiceFiltersDTO.getCity().toLowerCase()
                    )
            );
        }
        return spec;
    }

    private Specification<com.example.eventplanner.model.merchandise.Service> addGlobalSearch(Specification<com.example.eventplanner.model.merchandise.Service> spec, String search) {
        if (StringUtils.hasText(search)) {
            return spec.and((root, query, criteriaBuilder) -> {
                String searchPattern = "%" + search.toLowerCase() + "%";
                return criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), searchPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), searchPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("category").get("title")), searchPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("address").get("city")), searchPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("address").get("street")), searchPattern)
                );
            });
        }
        return spec;
    }

    private MerchandiseOverviewDTO convertToOverviewDTO(com.example.eventplanner.model.merchandise.Service service) {
        MerchandiseOverviewDTO dto = new MerchandiseOverviewDTO();
        dto.setId(service.getId());
        dto.setTitle(service.getTitle());
        dto.setDescription(service.getDescription());
        dto.setAddress(service.getAddress());
        dto.setCategory(service.getCategory().getTitle());
        if(service.getPhotos() != null && !service.getPhotos().isEmpty())
            dto.setPhotos(service.getPhotos().stream().map(this::mapToMerchandisePhotoDTO).toList());
        dto.setRating(service.getRating());
        dto.setType(service.getClass().getSimpleName());
        dto.setPrice(service.getPrice());
        return dto;
    }

    private ServiceOverviewDTO convertToServiceOverviewDTO(com.example.eventplanner.model.merchandise.Service service) {
        ServiceOverviewDTO dto = new ServiceOverviewDTO();
        dto.setId(service.getId());
        dto.setTitle(service.getTitle());
        dto.setDescription(service.getDescription());
        dto.setAddress(convertToAddressDTO(service.getAddress()));
        dto.setCategory(service.getCategory());
        if(service.getPhotos() != null && !service.getPhotos().isEmpty())
            dto.setPhotos(service.getPhotos().stream().map(this::mapToMerchandisePhotoDTO).toList());
        dto.setPrice(service.getPrice());
        return dto;
    }

    private AddressDTO convertToAddressDTO(Address address) {
        AddressDTO dto = new AddressDTO();
        dto.setCity(address.getCity());
        dto.setStreet(address.getStreet());
        dto.setNumber(address.getNumber());
        dto.setLatitude(address.getLatitude());
        dto.setLongitude(address.getLongitude());
        return dto;
    }

    public MerchandisePhotoDTO mapToMerchandisePhotoDTO(MerchandisePhoto merchandisePhoto) {
        MerchandisePhotoDTO photoDTO = new MerchandisePhotoDTO();
        photoDTO.setId(merchandisePhoto.getId());  // Set the photo ID
        photoDTO.setPhoto(merchandisePhoto.getPhoto());  // Set the byte array of the photo

        return photoDTO;
    }

    @Transactional
    public ReservationResponseDTO reserveService(int serviceId, ReservationRequestDTO request) {
        // Fetch the service
        // Fetch the service
        com.example.eventplanner.model.merchandise.Service service = serviceRepository.findAvailableServiceById(serviceId)
                .orElseThrow(() -> new ServiceReservationException(
                        "Service not found or unavailable",
                        ServiceReservationException.ErrorType.SERVICE_NOT_FOUND
                ));

        // Fetch the event
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ServiceReservationException(
                        "Event not found",
                        ServiceReservationException.ErrorType.EVENT_NOT_FOUND
                ));

        // Validate reservation timing
        try {
            validateReservationTiming(service, event, request);
        } catch (Exception e) {
            throw new ServiceReservationException(
                    "Reservation timing is invalid: " + e.getMessage(),
                    ServiceReservationException.ErrorType.TIMING_CONSTRAINT_VIOLATION
            );
        }

        // Check time slot availability
        try {
            checkTimeSlotAvailability(service, request);
        } catch (Exception e) {
            throw new ServiceReservationException(
                    "Time slot is not available",
                    ServiceReservationException.ErrorType.TIME_SLOT_ALREADY_BOOKED
            );
        }


        // Calculate end time if not provided
        LocalDateTime endTime = calculateEndTime(service, request);

        // Create and save time slot
        Timeslot timeslot = new Timeslot(request.getStartTime(), endTime);

        service.getTimeslots().add(timeslot);

        BudgetItem existingBudgetItem = event.getBudget()
                .getBudgetItems()
                .stream()
                .filter(item ->
                        item.getCategory().getId() == service.getCategory().getId() &&
                                item.getMerchandise() == null)
                .findFirst()
                .orElse(null);
        if(existingBudgetItem != null) {
            existingBudgetItem.setMerchandise(service);
        }
        else {
            BudgetItem budgetItem = new BudgetItem();
            budgetItem.setMerchandise(service);
            budgetItem.setCategory(service.getCategory());
            budgetItem.setMaxAmount(0);
            BudgetItem savedBudgetItem = budgetItemRepository.save(budgetItem);
            event.getBudget().getBudgetItems().add(savedBudgetItem);
        }

        budgetRepository.save(event.getBudget());

        // Save changes
        timeslotRepository.save(timeslot);
        serviceRepository.save(service);
        eventRepository.save(event);

        // Send notifications
        sendReservationNotifications(service, event);
        sendReservationEmail(request,serviceId);

        return mapToReservationResponse(service,event,request);
    }

    private void validateReservationDate(){

    }

    private void validateReservationTiming(com.example.eventplanner.model.merchandise.Service service, Event event, ReservationRequestDTO request) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reservationDeadline = event.getDate().minusMinutes(service.getReservationDeadline());
        LocalDateTime cancellationDeadline = event.getDate().minusMinutes(service.getCancellationDeadline());

        // Validate start time is within reservation deadline
        if (request.getStartTime().isAfter(event.getDate()) ||
                request.getStartTime().isBefore(reservationDeadline)) {
            throw new Exception("Reservation is outside allowed time frame");
        }

        // Validate duration constraints
        validateDurationConstraints(service, request);
    }

    private void validateDurationConstraints(com.example.eventplanner.model.merchandise.Service service, ReservationRequestDTO request) throws Exception {
        // Calculate duration
        long durationMinutes = Duration.between(
                request.getStartTime(),
                request.getEndTime() != null ? request.getEndTime() : request.getStartTime().plusMinutes(service.getMinDuration())
        ).toMinutes();

        if (durationMinutes < service.getMinDuration() ||
                (service.getMaxDuration() > 0 && durationMinutes > service.getMaxDuration())) {
            throw new Exception("Service duration does not meet constraints");
        }
    }

    private void checkTimeSlotAvailability(com.example.eventplanner.model.merchandise.Service service, ReservationRequestDTO request) throws Exception {
        LocalDateTime startTime = request.getStartTime();
        LocalDateTime endTime = request.getEndTime() != null
                ? request.getEndTime()
                : startTime.plusMinutes(service.getMinDuration());

        // Check for overlapping time slots within the service
        boolean isTimeSlotAvailable = service.getTimeslots().stream()
                .allMatch(existingSlot ->
                        endTime.isBefore(existingSlot.getStartTime()) ||
                                startTime.isAfter(existingSlot.getEndTime())
                );

        if (!isTimeSlotAvailable) {
            throw new Exception("Selected time slot is already booked");
        }
    }

    public List<TimeSlotDTO> getServiceTimeslots(int serviceId) {
        // Fetch the service
        com.example.eventplanner.model.merchandise.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Service not found with id: " + serviceId));

        // Convert timeslots to DTOs
        return service.getTimeslots().stream()
                .map(TimeSlotDTO::new)
                .collect(Collectors.toList());
    }

    private LocalDateTime calculateEndTime(com.example.eventplanner.model.merchandise.Service service, ReservationRequestDTO request) {
        if (request.getEndTime() != null) {
            return request.getEndTime();
        }

        // If end time not provided, calculate based on service duration
        return request.getStartTime().plusMinutes(service.getMinDuration());
    }

    private void sendReservationNotifications(com.example.eventplanner.model.merchandise.Service service, Event event) {

    }

    private void sendReservationEmail(ReservationRequestDTO request,int serviceId) {
        Optional<User> eventOrganizer=userRepository.findById(request.getOrganizerId());
        if (eventOrganizer.isEmpty()) {
            throw new RuntimeException("Organizer not found");
        }
        Optional<ServiceProvider> serviceProvider=serviceProviderRepository.findByMerchandiseId(serviceId);
        if (serviceProvider.isEmpty()) {
            throw new RuntimeException("provider not found");
        }
        emailService.sendMail(
            "system@eventplanner.com",
                serviceProvider.get().getUsername(),
                "Reservation for event "+ request.getEventId(),
                "Reservation successful"

        );
        emailService.sendMail(
                "system@eventplanner.com",
                eventOrganizer.get().getUsername(),
                "Reservation for event "+ request.getEventId(),
                "Reservation successful"

        );
    }

    private ReservationResponseDTO mapToReservationResponse(com.example.eventplanner.model.merchandise.Service service,Event event,ReservationRequestDTO request) {
        ReservationResponseDTO response = new ReservationResponseDTO();
        response.setServiceId(service.getId());
        response.setEventId(event.getId());
        response.setProviderId(request.getOrganizerId());
        response.setStartTime(request.getStartTime());
        response.setEndTime(request.getEndTime());
        Optional<ServiceProvider> serviceProvider=serviceProviderRepository.findByMerchandiseId(service.getId());
        if(serviceProvider.isPresent()) {
            response.setProviderEmail(serviceProvider.get().getUsername());
        }
        return response;
    }

    public CreateServiceResponseDTO createService(CreateServiceRequestDTO request) {
        ServiceProvider serviceProvider = (ServiceProvider) userRepository.findById(request.getServiceProviderId())
                .orElseThrow(() -> new RuntimeException(
                        "Service provider with id " + request.getServiceProviderId() + " not found"));

        com.example.eventplanner.model.merchandise.Service service = new com.example.eventplanner.model.merchandise.Service();
        service.setTitle(request.getTitle());
        service.setDescription(request.getDescription());
        service.setSpecificity(request.getSpecificity());
        service.setPrice(request.getPrice());
        service.setDiscount(request.getDiscount());
        service.setMinDuration(request.getMinDuration());
        service.setMaxDuration(request.getMaxDuration());
        service.setReservationDeadline(request.getReservationDeadline());
        service.setCancellationDeadline(request.getCancellationDeadline());
        service.setAutomaticReservation(request.isAutomaticReservation());

        if(request.getMerchandisePhotos() != null) {
            List<MerchandisePhoto> photos = request.getMerchandisePhotos().stream()
                    .map(photo -> {
                        MerchandisePhoto newPhoto = new MerchandisePhoto();
                        newPhoto.setPhoto(photo.getPhoto());
                        return newPhoto;
                    }).collect(Collectors.toList());
            List<MerchandisePhoto> savedPhotos = merchandisePhotoRepository.saveAll(photos);
            service.setPhotos(savedPhotos);
        }

        List<EventType> eventTypes = eventTypeRepository.findAllById(request.getEventTypesIds());
        service.setEventTypes(eventTypes);

        if(request.getCategoryId() != -1) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException(
                            "Category with id " + request.getCategoryId() + " not found"));
            service.setCategory(category);
            service.setState(MerchandiseState.APPROVED);
        } else {
            Category category = new Category();
            category.setTitle(request.getCategory().getTitle());
            category.setDescription(request.getCategory().getDescription());
            category.setPending(true);
            service.setCategory(categoryRepository.save(category));
            service.setState(MerchandiseState.PENDING);
        }

        Address address = new Address();
        if(request.getAddress() != null) {
            address.setStreet(request.getAddress().getStreet());
            address.setCity(request.getAddress().getCity());
            address.setNumber(request.getAddress().getNumber());
            address.setLongitude(request.getAddress().getLongitude());
            address.setLatitude(request.getAddress().getLatitude());
            service.setAddress(address);
        }else {
            address.setStreet(serviceProvider.getAddress().getStreet());
            address.setCity(serviceProvider.getAddress().getCity());
            address.setNumber(serviceProvider.getAddress().getNumber());
            address.setLongitude(serviceProvider.getAddress().getLongitude());
            address.setLatitude(serviceProvider.getAddress().getLatitude());
        }

        com.example.eventplanner.model.merchandise.Service savedService = merchandiseRepository.save(service);

        if(serviceProvider.getMerchandise() != null) {
            serviceProvider.getMerchandise().add(service);
            userRepository.save(serviceProvider);
        }
        return mapToCreateServiceResponseDTO(savedService, serviceProvider.getId());
    }

    private CreateServiceResponseDTO mapToCreateServiceResponseDTO(Merchandise savedService, int serviceProviderId) {
        CreateServiceResponseDTO response = new CreateServiceResponseDTO();

        response.setId(savedService.getId());
        response.setTitle(savedService.getTitle());
        response.setDescription(savedService.getDescription());
        response.setSpecificity(savedService.getSpecificity());
        response.setPrice(savedService.getPrice());
        response.setDiscount(savedService.getDiscount());
        response.setMinDuration(savedService.getMinDuration());
        response.setMaxDuration(savedService.getMaxDuration());
        response.setReservationDeadline(savedService.getReservationDeadline());
        response.setCancellationDeadline(savedService.getCancellationDeadline());
        response.setAutomaticReservation(savedService.isAutomaticReservation());
        response.setPhotos(savedService.getPhotos().stream().map(this::mapToMerchandisePhotoDTO).toList());
        response.setEventTypes(savedService.getEventTypes());
        response.setCategory(savedService.getCategory());
        response.setVisible(savedService.isVisible());
        response.setAvailable(savedService.isAvailable());

        AddressDTO address = new AddressDTO();
        address.setStreet(savedService.getAddress().getStreet());
        address.setCity(savedService.getAddress().getCity());
        address.setNumber(savedService.getAddress().getNumber());
        address.setLongitude(savedService.getAddress().getLongitude());
        address.setLatitude(savedService.getAddress().getLatitude());
        response.setAddress(address);
        response.setServiceProviderId(serviceProviderId);

        return response;
    }

    public List<CreateServiceResponseDTO> getAllBySpId(int id) {
        ServiceProvider serviceProvider = (ServiceProvider) userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service provider with id " + id + " not found"));

        return serviceProvider.getMerchandise().stream()
                .filter(merchandise -> !merchandise.isDeleted() &&
                        merchandise instanceof com.example.eventplanner.model.merchandise.Service)
                .map(merchandise -> mapToCreateServiceResponseDTO(merchandise, serviceProvider.getId())).toList();
    }

    private ServiceOverviewDTO maptoServiceOverviewDTO(Merchandise savedService) {
        ServiceOverviewDTO responseDTO = new ServiceOverviewDTO();

        responseDTO.setTitle(savedService.getTitle());
        responseDTO.setDescription(savedService.getDescription());
        responseDTO.setSpecificity(savedService.getSpecificity());
        responseDTO.setPrice(savedService.getPrice());
        responseDTO.setDiscount(savedService.getDiscount());
        responseDTO.setCategory(savedService.getCategory());
        responseDTO.setAvailable(savedService.isAvailable());
        responseDTO.setPhotos(savedService.getPhotos().stream().map(this::mapToMerchandisePhotoDTO).toList());
        responseDTO.setEventTypes(savedService.getEventTypes());

        return responseDTO;
    }

    public CreateServiceResponseDTO updateService(int serviceId, UpdateServiceRequestDTO request) {
        com.example.eventplanner.model.merchandise.Service serviceBeforeUpdate = serviceRepository.findById(serviceId).orElseThrow(
                () -> new RuntimeException("Service with id " + serviceId + " not found"));
        serviceBeforeUpdate.setAvailable(false);
        com.example.eventplanner.model.merchandise.Service updatedService = new com.example.eventplanner.model.merchandise.Service();

        ServiceProvider serviceProvider = (ServiceProvider) userRepository.findById(request.getServiceProviderId()).orElseThrow(
                () -> new RuntimeException("User with id " + request.getServiceProviderId() + " not found")
        );

        updatedService.setTitle(request.getTitle());
        updatedService.setDescription(request.getDescription());
        updatedService.setSpecificity(request.getSpecificity());
        updatedService.setPrice(request.getPrice());
        updatedService.setDiscount(request.getDiscount());
        updatedService.setMinDuration(request.getMinDuration());
        updatedService.setMaxDuration(request.getMaxDuration());
        updatedService.setReservationDeadline(request.getReservationDeadline());
        updatedService.setCancellationDeadline(request.getCancellationDeadline());
        updatedService.setAutomaticReservation(request.isAutomaticReservation());
        updatedService.setAvailable(request.isAvailable());
        updatedService.setVisible(request.isVisible());

        if(request.getPhotos() != null) {
            List<MerchandisePhoto> photos = request.getPhotos().stream().map(photoDto -> {
                MerchandisePhoto newPhoto = new MerchandisePhoto();
                newPhoto.setPhoto(photoDto.getPhoto());
                return newPhoto;
            }).toList();
            List<MerchandisePhoto> savedPhotos = merchandisePhotoRepository.saveAll(photos);
            updatedService.setPhotos(savedPhotos);
        }

        List<EventType> eventTypes = eventTypeRepository.findAllById(request.getEventTypesIds());
        updatedService.setEventTypes(eventTypes);

        Address address = new Address();
        if(request.getAddress() != null) {
            address.setStreet(request.getAddress().getStreet());
            address.setCity(request.getAddress().getCity());
            address.setNumber(request.getAddress().getNumber());
            address.setLongitude(request.getAddress().getLongitude());
            address.setLatitude(request.getAddress().getLatitude());
        }else {
            address.setStreet(serviceProvider.getAddress().getStreet());
            address.setCity(serviceProvider.getAddress().getCity());
            address.setNumber(serviceProvider.getAddress().getNumber());
            address.setLongitude(serviceProvider.getAddress().getLongitude());
            address.setLatitude(serviceProvider.getAddress().getLatitude());
        }
        updatedService.setAddress(address);
        updatedService.setCategory(serviceBeforeUpdate.getCategory());

        List<Review> merchandiseReviews = new ArrayList<>(serviceBeforeUpdate.getReviews());
        serviceBeforeUpdate.setReviews(new ArrayList<>());
        merchandiseRepository.save(serviceBeforeUpdate);
        updatedService.setReviews(merchandiseReviews);

        com.example.eventplanner.model.merchandise.Service savedService = merchandiseRepository.save(updatedService);

        if(serviceProvider.getMerchandise() != null) {
            serviceProvider.getMerchandise().remove(serviceBeforeUpdate);
            serviceProvider.getMerchandise().add(savedService);
            userRepository.save(serviceProvider);
        }
        return mapToCreateServiceResponseDTO(updatedService, serviceProvider.getId());
    }

    public void deleteService(int serviceId) {
        Merchandise service = merchandiseRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Service with id " + serviceId + " not found"));
        service.setDeleted(true);
        merchandiseRepository.save(service);
    }
}
