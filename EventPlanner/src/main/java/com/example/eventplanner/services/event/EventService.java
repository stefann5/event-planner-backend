package com.example.eventplanner.services.event;

import com.example.eventplanner.dto.event.EventOverviewDTO;
import com.example.eventplanner.dto.filter.EventFiltersDTO;
import com.example.eventplanner.dto.common.AddressDTO;
import com.example.eventplanner.dto.event.*;
import com.example.eventplanner.dto.eventType.EventTypeOverviewDTO;
import com.example.eventplanner.dto.merchandise.product.GetProductByIdResponseDTO;
import com.example.eventplanner.dto.merchandise.service.GetServiceByIdResponseDTO;
import com.example.eventplanner.dto.review.ReviewDTO;
import com.example.eventplanner.dto.user.UserOverviewDTO;
import com.example.eventplanner.model.common.Address;
import com.example.eventplanner.model.event.Activity;
import com.example.eventplanner.model.event.Event;
import com.example.eventplanner.model.event.EventType;
import com.example.eventplanner.model.merchandise.Merchandise;
import com.example.eventplanner.model.merchandise.Product;
import com.example.eventplanner.model.merchandise.Review;
import com.example.eventplanner.model.merchandise.ReviewStatus;
import com.example.eventplanner.model.user.EventOrganizer;
import com.example.eventplanner.model.user.User;
import com.example.eventplanner.repositories.event.ActivityRepository;
import com.example.eventplanner.repositories.event.EventRepository;
import com.example.eventplanner.repositories.eventType.EventTypeRepository;
import com.example.eventplanner.repositories.merchandise.MerchandiseRepository;
import com.example.eventplanner.repositories.user.EventOrganizerRepository;
import com.example.eventplanner.repositories.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {
    private final EventRepository eventRepository;
    private final EventOrganizerRepository eventOrganizerRepository;
    private final EventTypeRepository eventTypeRepository;
    private final MerchandiseRepository merchandiseRepository;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;

    public Page<EventOverviewDTO> getTop(Pageable pageable) {
        return eventRepository.findAll(pageable)
                .map(this::convertToOverviewDTO);
    }

    public Page<EventOverviewDTO> getAll(Pageable pageable) {
        return eventRepository.findAll(pageable)
                .map(this::convertToOverviewDTO);
    }

    public CreatedEventOverviewDTO getById(int id) {
        return mapToCreatedEventOverviewDTO(eventRepository.findById(id).orElseThrow(), eventRepository.findById(id).orElseThrow().getType(), eventRepository.findById(id).orElseThrow().getMerchandise());
    }

    public Page<EventOverviewDTO> getByEo(int id, Pageable pageable) {
        return eventRepository.findByOrganizerId(id, pageable).map(this::convertToOverviewDTO);
    }

    public EventReportDTO getEventReport(int id){
        EventReportDTO eventReportDTO = new EventReportDTO();
        Event event = eventRepository.findById(id).orElseThrow();

        eventReportDTO.setParticipants(event.getParticipants().stream().map(this::convertToUserDTO).toList());
        eventReportDTO.setReviews(event.getReviews().stream().filter(review -> review.getStatus() == ReviewStatus.APPROVED).map(this::convertToReviewDTO).toList());

        return eventReportDTO;
    }

    private ReviewDTO convertToReviewDTO(Review review){
        ReviewDTO reviewDTO = new ReviewDTO();
        reviewDTO.setId(review.getId());
        reviewDTO.setComment(review.getComment());
        reviewDTO.setRating(review.getRating());
        reviewDTO.setStatus(true);
        return reviewDTO;
    }

    private UserOverviewDTO convertToUserDTO(User user){
        UserOverviewDTO userOverviewDTO = new UserOverviewDTO(user.getId(), user.getUsername(), user.getName(), user.getSurname(), "");
        return userOverviewDTO;
    }

    public List<EventOverviewDTO> getUserFollowedEvents(int userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        return user.getFollowedEvents().stream()
                .map(this::convertToOverviewDTO)
                .collect(Collectors.toList());
    }

    public Page<EventOverviewDTO> search(EventFiltersDTO eventFiltersDTO, String search, Pageable pageable) {
        Specification<Event> spec = createSpecification(eventFiltersDTO, search);
        Page<Event> events = eventRepository.findAll(spec, pageable);
        return events.map(this::convertToOverviewDTO);
    }

    public Boolean favorizeEvent(int eventId, int userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found with id: " + eventId));

        if(user.getFavoriteEvents().contains(event)) {
            user.getFavoriteEvents().remove(event);
        }
        else{
            user.getFavoriteEvents().add(event);
        }


        userRepository.save(user);

        return true;
    }

    private Specification<Event> createSpecification(EventFiltersDTO eventFiltersDTO, String search) {
        Specification<Event> spec = Specification.where(null);
        spec = addDateRangeFilter(spec, eventFiltersDTO);
        spec = addTypeFilter(spec, eventFiltersDTO);
        spec = addCityFilter(spec, eventFiltersDTO);
        spec = addGlobalSearch(spec, search);
        return spec;
    }

    private Specification<Event> addDateRangeFilter(Specification<Event> spec, EventFiltersDTO eventFiltersDTO) {
        if (eventFiltersDTO.getStartDate() != null && eventFiltersDTO.getEndDate() != null) {
            return spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.between(root.get("date"),
                            eventFiltersDTO.getStartDate(),
                            eventFiltersDTO.getEndDate())
            );
        }
        return spec;
    }

    private Specification<Event> addTypeFilter(Specification<Event> spec, EventFiltersDTO eventFiltersDTO) {
        if (StringUtils.hasText(eventFiltersDTO.getType())) {
            return spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                            root.get("type").get("title"),
                            eventFiltersDTO.getType()
                    )
            );
        }
        return spec;
    }

    private Specification<Event> addCityFilter(Specification<Event> spec, EventFiltersDTO eventFiltersDTO) {
        if (StringUtils.hasText(eventFiltersDTO.getCity())) {
            return spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                            root.get("address").get("city"),
                            eventFiltersDTO.getCity()
                    )
            );
        }
        return spec;
    }

    private Specification<Event> addGlobalSearch(Specification<Event> spec, String search) {
        if (StringUtils.hasText(search)) {
            return spec.and((root, query, criteriaBuilder) -> {
                String searchPattern = "%" + search.toLowerCase() + "%";
                return criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), searchPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), searchPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("address").get("city")), searchPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("type").get("title")), searchPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("address").get("street")), searchPattern)
                );
            });
        }
        return spec;
    }
    private EventTypeOverviewDTO convertToOverviewDTO(EventType eventType) {
        EventTypeOverviewDTO dto = new EventTypeOverviewDTO();
        dto.setId(eventType.getId());
        dto.setTitle(eventType.getTitle());
        dto.setDescription(eventType.getDescription());
        dto.setActive(eventType.isActive());
        dto.setRecommendedCategories(null);

        return dto;
    }
    public EventDetailsDTO getDetails(int eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        EventDetailsDTO dto = new EventDetailsDTO();
        dto.setId(event.getId());
        dto.setEventType(convertToOverviewDTO(event.getType()));
        dto.setTitle(event.getTitle());
        dto.setDate(event.getDate());

        Address address = event.getAddress();
        if (address != null) {
            AddressDTO addressDTO = new AddressDTO();
            addressDTO.setCity(address.getCity());
            addressDTO.setStreet(address.getStreet());
            addressDTO.setNumber(address.getNumber());
            addressDTO.setLatitude(address.getLatitude());
            addressDTO.setLongitude(address.getLongitude());
            dto.setAddress(addressDTO);
        }

        dto.setDescription(event.getDescription());
        dto.setMaxParticipants(event.getMaxParticipants());
        dto.setPublic(event.isPublic());
        dto.setOrganizer(convertToEoDTO(event.getOrganizer()));

        return dto;
    }

    public CreatedEventOverviewDTO createEvent(CreateEventDTO dto) {
        Event event = new Event();
        event.setTitle(dto.getTitle());
        event.setDescription(dto.getDescription());
        event.setMaxParticipants(dto.getMaxParticipants());
        event.setPublic(dto.isPublic());
        event.setDate(dto.getDate());
        Address address = new Address();
        address.setCity(dto.getAddress().getCity());
        address.setStreet(dto.getAddress().getStreet());
        address.setNumber(dto.getAddress().getNumber());
        address.setLatitude(dto.getAddress().getLatitude());
        address.setLongitude(dto.getAddress().getLongitude());
        event.setAddress(address);

        EventType eventType = eventTypeRepository.findById(dto.getEventTypeId()).orElseThrow();
        event.setType(eventType);
        List<Merchandise> products = merchandiseRepository.findAllById(dto.getProductIds());
        List<Merchandise> services = merchandiseRepository.findAllById(dto.getServiceIds());
        List<Merchandise> merchandise = new ArrayList<>();

        if(!products.isEmpty()){
            merchandise.addAll(products);
        }
        if(!services.isEmpty()){
            merchandise.addAll(services);
        }
        event.setMerchandise(merchandise);

        EventOrganizer eventOrganizer = eventOrganizerRepository.findById(dto.getOrganizerId()).orElseThrow();
        event.setOrganizer(eventOrganizer);

        Event savedEvent = eventRepository.save(event);

        return mapToCreatedEventOverviewDTO(savedEvent, eventType, merchandise);
    }

    private CreatedEventOverviewDTO mapToCreatedEventOverviewDTO(Event event, EventType eventType, List<Merchandise> merchandise) {
        CreatedEventOverviewDTO dto = new CreatedEventOverviewDTO();
        dto.setId(event.getId());
        dto.setTitle(event.getTitle());
        dto.setDescription(event.getDescription());
        dto.setMaxParticipants(event.getMaxParticipants());
        dto.setPublic(event.isPublic());
        dto.setDate(event.getDate());
        dto.setOrganizer(convertToEoDTO(event.getOrganizer()));

        // Map address
        AddressDTO addressDTO = new AddressDTO();
        addressDTO.setCity(event.getAddress().getCity());
        addressDTO.setStreet(event.getAddress().getStreet());
        addressDTO.setNumber(event.getAddress().getNumber());
        addressDTO.setLatitude(event.getAddress().getLatitude());
        addressDTO.setLongitude(event.getAddress().getLongitude());
        dto.setAddress(addressDTO);

        // Map event types

        EventTypeOverviewDTO typeDTO = new EventTypeOverviewDTO();
        typeDTO.setId(eventType.getId());
        typeDTO.setTitle(eventType.getTitle());
        typeDTO.setDescription(eventType.getDescription());
        typeDTO.setActive(eventType.isActive());

        dto.setEventType(typeDTO);

        // Separate and map merchandise into Products and Services
        List<GetProductByIdResponseDTO> productDTOs = merchandise.stream()
                .filter(merch -> merch instanceof Product)
                .map(merch -> {
                    Product product = (Product) merch;
                    GetProductByIdResponseDTO productDTO = new GetProductByIdResponseDTO();
                    productDTO.setId(product.getId());
                    productDTO.setTitle(product.getTitle());
                    productDTO.setDescription(product.getDescription());
                    return productDTO;
                })
                .toList();
        dto.setProducts(productDTOs);

        List<GetServiceByIdResponseDTO> serviceDTOs = merchandise.stream()
                .filter(merch -> merch instanceof com.example.eventplanner.model.merchandise.Service)
                .map(merch -> {
                    com.example.eventplanner.model.merchandise.Service service = (com.example.eventplanner.model.merchandise.Service) merch;
                    GetServiceByIdResponseDTO serviceDTO = new GetServiceByIdResponseDTO();
                    serviceDTO.setId(service.getId());
                    serviceDTO.setTitle(service.getTitle());
                    serviceDTO.setDescription(service.getDescription());
                    return serviceDTO;
                })
                .toList();
        dto.setServices(serviceDTOs);

        return dto;
    }

    private EventOrganizerDTO convertToEoDTO(EventOrganizer eventOrganizer) {
        EventOrganizerDTO eventOrganizerDTO = new EventOrganizerDTO();
        eventOrganizerDTO.setId(eventOrganizer.getId());
        eventOrganizerDTO.setEmail(eventOrganizer.getUsername());
        eventOrganizerDTO.setName(eventOrganizer.getName());
        eventOrganizerDTO.setSurname(eventOrganizer.getSurname());
        return eventOrganizerDTO;
    }

    private EventOverviewDTO convertToOverviewDTO(Event event) {
        EventOverviewDTO dto = new EventOverviewDTO();
        dto.setId(event.getId());
        dto.setTitle(event.getTitle());
        dto.setDescription(event.getDescription());
        dto.setDate(event.getDate());
        dto.setAddress(event.getAddress());
        dto.setType(event.getType() != null ? event.getType().getTitle() : null);
        return dto;
    }

    public CreatedEventOverviewDTO updateEvent(int eventId, UpdateEventDTO dto) {
        // Fetch the existing event
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        event.setTitle(dto.getTitle());
        event.setDescription(dto.getDescription());
        event.setMaxParticipants(dto.getMaxParticipants());
        event.setPublic(dto.isPublic());
        event.setDate(dto.getDate());
        Address address = new Address();
        address.setCity(dto.getAddress().getCity());
        address.setStreet(dto.getAddress().getStreet());
        address.setNumber(dto.getAddress().getNumber());
        address.setLatitude(dto.getAddress().getLatitude());
        address.setLongitude(dto.getAddress().getLongitude());
        event.setAddress(address);

        EventType eventType = eventTypeRepository.findById(dto.getEventTypeId()).orElseThrow();
        event.setType(eventType);
        List<Merchandise> products = merchandiseRepository.findAllById(dto.getProductIds());
        List<Merchandise> services = merchandiseRepository.findAllById(dto.getServiceIds());
        List<Merchandise> merchandise = new ArrayList<>();

        if(!products.isEmpty()){
            merchandise.addAll(products);
        }
        if(!services.isEmpty()){
            merchandise.addAll(services);
        }
        event.setMerchandise(merchandise);

        Event savedEvent = eventRepository.save(event);

        return mapToCreatedEventOverviewDTO(savedEvent, eventType, merchandise);
    }

    public CreatedActivityDTO updateAgenda(int eventId, CreateActivityDTO dto) {
        // Fetch the existing event
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        // Create a new Activity entity from the DTO
        Activity activity = new Activity();
        activity.setTitle(dto.getTitle());
        activity.setDescription(dto.getDescription());
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());

        Address address = new Address();
        address.setStreet(dto.getAddress().getStreet());
        address.setCity(dto.getAddress().getCity());
        address.setNumber(dto.getAddress().getNumber());
        address.setLatitude(dto.getAddress().getLatitude());
        address.setLongitude(dto.getAddress().getLongitude());
        activity.setAddress(address);

        // Save the activity (if using a repository for Activity)
        activity = activityRepository.save(activity);

        // Append the new activity to the event's list of activities
        event.getActivities().add(activity);

        // Save the updated event
        Event savedEvent = eventRepository.save(event);

        // Map the saved Activity to CreatedActivityDTO
        CreatedActivityDTO createdActivityDTO = new CreatedActivityDTO();
        createdActivityDTO.setId(activity.getId());
        createdActivityDTO.setTitle(activity.getTitle());
        createdActivityDTO.setDescription(activity.getDescription());
        createdActivityDTO.setStartTime(activity.getStartTime());
        createdActivityDTO.setEndTime(activity.getEndTime());

        AddressDTO addressDTO = new AddressDTO();
        addressDTO.setStreet(activity.getAddress().getStreet());
        addressDTO.setCity(activity.getAddress().getCity());
        addressDTO.setNumber(activity.getAddress().getNumber());
        addressDTO.setLatitude(activity.getAddress().getLatitude());
        addressDTO.setLongitude(activity.getAddress().getLongitude());
        createdActivityDTO.setAddress(addressDTO);

        return createdActivityDTO;
    }

    public List<ActivityOverviewDTO> getAgenda(int eventId) {
        // Fetch the existing event
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        return event.getActivities().stream().map(this::mapToActivityOverviewDTO).toList();
    }

    public ActivityOverviewDTO updateActivity(int activityId, CreateActivityDTO dto) {
        Activity activity = activityRepository.findById(activityId).orElseThrow(() -> new RuntimeException("Activity not found"));

        activity.setTitle(dto.getTitle());
        activity.setDescription(dto.getDescription());
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());
        activity = activityRepository.save(activity);

        return mapToActivityOverviewDTO(activity);
    }

    private ActivityOverviewDTO mapToActivityOverviewDTO(Activity activity) {
        ActivityOverviewDTO dto = new ActivityOverviewDTO();
        dto.setId(activity.getId());
        dto.setTitle(activity.getTitle());
        dto.setDescription(activity.getDescription());
        dto.setStartTime(activity.getStartTime());
        dto.setEndTime(activity.getEndTime());

        AddressDTO addressDTO = new AddressDTO();
        addressDTO.setStreet(activity.getAddress().getStreet());
        addressDTO.setCity(activity.getAddress().getCity());
        addressDTO.setNumber(activity.getAddress().getNumber());
        addressDTO.setLatitude(activity.getAddress().getLatitude());
        addressDTO.setLongitude(activity.getAddress().getLongitude());
        dto.setAddress(addressDTO);

        return dto;
    }

    public List<ActivityOverviewDTO> deleteActivity(int eventId, int activityId) {
        // Fetch the existing event
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        // Find and remove the activity from the event
        Activity activityToRemove = event.getActivities().stream()
                .filter(activity -> activity.getId() == activityId)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Activity not found"));

        event.getActivities().remove(activityToRemove);
        eventRepository.save(event); // Save the updated event

        // Optionally delete the activity from the database if it's no longer needed
        activityRepository.deleteById(activityId);

        // Map the updated list of activities to ActivityOverviewDTO
        return event.getActivities().stream()
                .map(this::mapToActivityOverviewDTO)
                .toList();
    }
}
