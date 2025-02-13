package com.example.eventplanner.merchandise.reserveservice;

import com.example.eventplanner.dto.merchandise.service.ReservationRequestDTO;
import com.example.eventplanner.dto.merchandise.service.ReservationResponseDTO;
import com.example.eventplanner.exceptions.ServiceReservationException;
import com.example.eventplanner.exceptions.UserAuthenticationException;
import com.example.eventplanner.model.common.Address;
import com.example.eventplanner.model.event.Budget;
import com.example.eventplanner.model.event.BudgetItem;
import com.example.eventplanner.model.event.Category;
import com.example.eventplanner.model.event.Event;
import com.example.eventplanner.model.merchandise.Service;
import com.example.eventplanner.model.merchandise.Timeslot;
import com.example.eventplanner.model.user.EventOrganizer;
import com.example.eventplanner.model.user.ServiceProvider;
import com.example.eventplanner.repositories.budget.BudgetItemRepository;
import com.example.eventplanner.repositories.budget.BudgetRepository;
import com.example.eventplanner.repositories.event.EventRepository;
import com.example.eventplanner.repositories.merchandise.ServiceRepository;
import com.example.eventplanner.repositories.merchandise.TimeslotRepository;
import com.example.eventplanner.repositories.user.ServiceProviderRepository;
import com.example.eventplanner.repositories.user.UserRepository;
import com.example.eventplanner.services.clock.ReservationNotificationScheduler;
import com.example.eventplanner.services.email.EmailService;
import com.example.eventplanner.services.merchandise.ServiceService;
import com.example.eventplanner.services.notification.NotificationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Service Reservation Service Tests")
class ServiceReservationServiceTest {
    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private TimeslotRepository timeslotRepository;
    @Mock
    private BudgetItemRepository budgetItemRepository;
    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ServiceProviderRepository serviceProviderRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ReservationNotificationScheduler reservationNotificationScheduler;

    @InjectMocks
    private ServiceService reservationService;

    private Service service;
    private Event event;
    private ReservationRequestDTO validRequest;
    private LocalDateTime eventDate;
    private EventOrganizer organizer;
    private ServiceProvider provider;

    @BeforeEach
    void setUp() {
        eventDate = LocalDateTime.now().plusDays(7);

        service = new Service();
        service.setId(1);
        service.setMinDuration(60);
        service.setMaxDuration(180);
        service.setReservationDeadline(1440); // 24 hours
        service.setTimeslots(new ArrayList<>());
        service.setCategory(new Category());

        organizer = new EventOrganizer();
        organizer.setId(1);
        organizer.setUsername("organizer@test.com");
        organizer.setNotifications(new ArrayList<>());

        event = new Event();
        event.setDate(eventDate);
        event.setTitle("Test Event");
        event.setId(1);
        Address address = new Address();
        address.setCity("City");
        address.setStreet("Street");
        address.setNumber("1");
        event.setAddress(address);
        event.setBudget(new Budget());
        event.getBudget().setBudgetItems(new ArrayList<>());
        event.setOrganizer(organizer);

        provider = new ServiceProvider();
        provider.setUsername("provider@test.com");

        validRequest = new ReservationRequestDTO();
        validRequest.setEventId(1);
        validRequest.setStartTime(eventDate.minusHours(2));
        validRequest.setOrganizerId(1);
    }

    @Test
    @DisplayName("Should successfully reserve service with valid request")
    @Tag("success")
    void reserveService_ValidRequest_Success() {
        when(serviceRepository.findAvailableServiceById(1)).thenReturn(Optional.of(service));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(userRepository.findById(1)).thenReturn(Optional.of(organizer));
        when(serviceProviderRepository.findByMerchandiseId(1)).thenReturn(Optional.of(provider));


        ReservationResponseDTO response = reservationService.reserveService(1, validRequest);

        // Verify response is not null and contains correct data
        assertNotNull(response, "Response should not be null");
        assertEquals(provider.getId(), response.getProviderId(), "Provider ID should match");
        assertEquals(event.getId(), response.getEventId(), "Event ID should match");
        assertEquals(service.getId(), response.getServiceId(), "Service ID should match");
        assertEquals(validRequest.getStartTime(), response.getStartTime(), "Start time should match request");
        assertEquals(validRequest.getEndTime(), response.getEndTime(), "End time should match request");
        assertEquals(provider.getUsername(), response.getProviderEmail(), "Provider email should match");

        // Verify the service and event were saved
        verify(serviceRepository).save(service);
        verify(eventRepository).save(event);
        verify(budgetItemRepository).save(any());
        verify(timeslotRepository).save(any(Timeslot.class));
    }

    @Test
    @DisplayName("Should throw exception when service not found")
    @Tag("not-found")
    void reserveService_ServiceNotFound_ThrowsException() {
        when(serviceRepository.findAvailableServiceById(1)).thenReturn(Optional.empty());

        ServiceReservationException exception = assertThrows(
                ServiceReservationException.class,
                () -> reservationService.reserveService(1, validRequest)
        );
        assertEquals(ServiceReservationException.ErrorType.SERVICE_NOT_FOUND, exception.getErrorType());
    }

    @Test
    @DisplayName("Should throw exception when reservation is for past date")
    @Tag("timing")
    void reserveService_PastDate_ThrowsException() {
        when(serviceRepository.findAvailableServiceById(1)).thenReturn(Optional.of(service));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        validRequest.setStartTime(LocalDateTime.now().minusDays(1));

        ServiceReservationException exception = assertThrows(
                ServiceReservationException.class,
                () -> reservationService.reserveService(1, validRequest)
        );
        assertEquals(ServiceReservationException.ErrorType.TIMING_CONSTRAINT_VIOLATION, exception.getErrorType());
    }

    @Test
    @DisplayName("Should throw exception when timeslot overlaps")
    @Tag("conflict")
    void reserveService_TimeSlotOverlap_ThrowsException() {
        when(serviceRepository.findAvailableServiceById(1)).thenReturn(Optional.of(service));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        Timeslot existingSlot = new Timeslot(
                validRequest.getStartTime(),
                validRequest.getStartTime().plusHours(1),
                event
        );
        service.getTimeslots().add(existingSlot);

        ServiceReservationException exception = assertThrows(
                ServiceReservationException.class,
                () -> reservationService.reserveService(1, validRequest)
        );
        assertEquals(ServiceReservationException.ErrorType.TIME_SLOT_ALREADY_BOOKED, exception.getErrorType());
    }

    @Test
    @DisplayName("Should throw exception when duration is too long")
    @Tag("timing")
    void reserveService_DurationTooLong_ThrowsException() {
        when(serviceRepository.findAvailableServiceById(1)).thenReturn(Optional.of(service));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        validRequest.setEndTime(validRequest.getStartTime().plusMinutes(service.getMaxDuration()+1));

        ServiceReservationException exception = assertThrows(
                ServiceReservationException.class,
                () -> reservationService.reserveService(1, validRequest)
        );
        assertEquals(ServiceReservationException.ErrorType.TIMING_CONSTRAINT_VIOLATION, exception.getErrorType());
    }

    @Test
    @DisplayName("Should throw exception when duration is too short")
    @Tag("timing")
    void reserveService_DurationTooShort_ThrowsException() {
        when(serviceRepository.findAvailableServiceById(1)).thenReturn(Optional.of(service));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        validRequest.setEndTime(validRequest.getStartTime().plusMinutes(service.getMinDuration()-1));

        ServiceReservationException exception = assertThrows(
                ServiceReservationException.class,
                () -> reservationService.reserveService(1, validRequest)
        );
        assertEquals(ServiceReservationException.ErrorType.TIMING_CONSTRAINT_VIOLATION, exception.getErrorType());
    }

    @Test
    @DisplayName("Should throw exception when before reservation deadline")
    @Tag("timing")
    void reserveService_BeforeReservationDeadline_ThrowsException() {
        when(serviceRepository.findAvailableServiceById(1)).thenReturn(Optional.of(service));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        validRequest.setStartTime(eventDate.minusMinutes(service.getReservationDeadline() + 1));

        ServiceReservationException exception = assertThrows(
                ServiceReservationException.class,
                () -> reservationService.reserveService(1, validRequest)
        );
        assertEquals(ServiceReservationException.ErrorType.TIMING_CONSTRAINT_VIOLATION, exception.getErrorType());
    }

    @Test
    @DisplayName("Should throw exception when event not found")
    @Tag("not-found")
    void reserveService_EventNotFound_ThrowsException() {
        when(serviceRepository.findAvailableServiceById(1)).thenReturn(Optional.of(service));
        when(eventRepository.findById(1)).thenReturn(Optional.empty());

        ServiceReservationException exception = assertThrows(
                ServiceReservationException.class,
                () -> reservationService.reserveService(1, validRequest)
        );
        assertEquals(ServiceReservationException.ErrorType.EVENT_NOT_FOUND, exception.getErrorType());
    }

    @Test
    @DisplayName("Should throw exception when organizer not found")
    @Tag("not-found")
    void reserveService_OrganizerNotFound_ThrowsException() {
        when(serviceRepository.findAvailableServiceById(1)).thenReturn(Optional.of(service));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(userRepository.findById(1)).thenReturn(Optional.empty());

        UserAuthenticationException exception= assertThrows(UserAuthenticationException.class,
                () -> reservationService.reserveService(1, validRequest));
        assertEquals(UserAuthenticationException.ErrorType.USER_NOT_FOUND, exception.getErrorType());
    }

    @Test
    @DisplayName("Should throw exception when service provider not found")
    @Tag("not-found")
    void reserveService_ServiceProviderNotFound_ThrowsException() {
        when(serviceRepository.findAvailableServiceById(1)).thenReturn(Optional.of(service));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(userRepository.findById(1)).thenReturn(Optional.of(organizer));
        when(serviceProviderRepository.findByMerchandiseId(1)).thenReturn(Optional.empty());

        UserAuthenticationException exception=assertThrows(UserAuthenticationException.class,
                () -> reservationService.reserveService(1, validRequest));
        assertEquals(UserAuthenticationException.ErrorType.USER_NOT_FOUND, exception.getErrorType());

    }

    @Test
    @DisplayName("Should successfully reserve service exactly at reservation deadline")
    @Tag("success")
    void reserveService_ExactlyAtReservationDeadline_Success() {
        when(serviceRepository.findAvailableServiceById(1)).thenReturn(Optional.of(service));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(userRepository.findById(1)).thenReturn(Optional.of(organizer));
        when(serviceProviderRepository.findByMerchandiseId(1)).thenReturn(Optional.of(provider));


        ReservationResponseDTO response = reservationService.reserveService(1, validRequest);

        // Verify response is not null and contains correct data
        assertNotNull(response, "Response should not be null");
        assertEquals(provider.getId(), response.getProviderId(), "Provider ID should match");
        assertEquals(event.getId(), response.getEventId(), "Event ID should match");
        assertEquals(service.getId(), response.getServiceId(), "Service ID should match");
        assertEquals(validRequest.getStartTime(), response.getStartTime(), "Start time should match request");
        assertEquals(validRequest.getEndTime(), response.getEndTime(), "End time should match request");
        assertEquals(provider.getUsername(), response.getProviderEmail(), "Provider email should match");

        // Verify the service and event were saved
        verify(serviceRepository).save(service);
        verify(eventRepository).save(event);
        verify(budgetItemRepository).save(any());
        verify(timeslotRepository).save(any(Timeslot.class));
    }

    @Test
    @DisplayName("Should successfully reserve service with exact minimum duration")
    @Tag("success")
    void reserveService_ExactMinDuration_Success() {
        when(serviceRepository.findAvailableServiceById(1)).thenReturn(Optional.of(service));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(userRepository.findById(1)).thenReturn(Optional.of(organizer));
        when(serviceProviderRepository.findByMerchandiseId(1)).thenReturn(Optional.of(provider));

        validRequest.setEndTime(validRequest.getStartTime().plusMinutes(service.getMinDuration()));


        ReservationResponseDTO response = reservationService.reserveService(1, validRequest);

        // Verify response is not null and contains correct data
        assertNotNull(response, "Response should not be null");
        assertEquals(provider.getId(), response.getProviderId(), "Provider ID should match");
        assertEquals(event.getId(), response.getEventId(), "Event ID should match");
        assertEquals(service.getId(), response.getServiceId(), "Service ID should match");
        assertEquals(validRequest.getStartTime(), response.getStartTime(), "Start time should match request");
        assertEquals(validRequest.getEndTime(), response.getEndTime(), "End time should match request");
        assertEquals(provider.getUsername(), response.getProviderEmail(), "Provider email should match");

        // Verify the service and event were saved
        verify(serviceRepository).save(service);
        verify(eventRepository).save(event);
        verify(budgetItemRepository).save(any());
        verify(timeslotRepository).save(any(Timeslot.class));
    }

    @Test
    @DisplayName("Should successfully reserve service with exact maximum duration")
    @Tag("success")
    void reserveService_ExactMaxDuration_Success() {
        when(serviceRepository.findAvailableServiceById(1)).thenReturn(Optional.of(service));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(userRepository.findById(1)).thenReturn(Optional.of(organizer));
        when(serviceProviderRepository.findByMerchandiseId(1)).thenReturn(Optional.of(provider));

        validRequest.setEndTime(validRequest.getStartTime().plusMinutes(service.getMaxDuration()));


        ReservationResponseDTO response = reservationService.reserveService(1, validRequest);

        // Verify response is not null and contains correct data
        assertNotNull(response, "Response should not be null");
        assertEquals(provider.getId(), response.getProviderId(), "Provider ID should match");
        assertEquals(event.getId(), response.getEventId(), "Event ID should match");
        assertEquals(service.getId(), response.getServiceId(), "Service ID should match");
        assertEquals(validRequest.getStartTime(), response.getStartTime(), "Start time should match request");
        assertEquals(validRequest.getEndTime(), response.getEndTime(), "End time should match request");
        assertEquals(provider.getUsername(), response.getProviderEmail(), "Provider email should match");

        // Verify the service and event were saved
        verify(serviceRepository).save(service);
        verify(eventRepository).save(event);
        verify(budgetItemRepository).save(any());
        verify(timeslotRepository).save(any(Timeslot.class));
    }

    @Test
    @DisplayName("Should update existing budget item when present")
    @Tag("budget")
    void reserveService_WithExistingBudgetItem_UpdatesExistingItem() {
        when(serviceRepository.findAvailableServiceById(1)).thenReturn(Optional.of(service));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(userRepository.findById(1)).thenReturn(Optional.of(organizer));
        when(serviceProviderRepository.findByMerchandiseId(1)).thenReturn(Optional.of(provider));

        Category category = new Category();
        category.setId(1);
        service.setCategory(category);

        BudgetItem existingItem = new BudgetItem();
        existingItem.setCategory(category);
        event.getBudget().getBudgetItems().add(existingItem);

        reservationService.reserveService(1, validRequest);

        assertEquals(service, existingItem.getMerchandise());
    }

    @Test
    @DisplayName("Should successfully reserve service with timeslot bordering existing")
    @Tag("success")
    void reserveService_TimeslotBorderingExisting_Success() {
        when(serviceRepository.findAvailableServiceById(1)).thenReturn(Optional.of(service));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(userRepository.findById(1)).thenReturn(Optional.of(organizer));
        when(serviceProviderRepository.findByMerchandiseId(1)).thenReturn(Optional.of(provider));

        Timeslot existingSlot = new Timeslot(
                eventDate.minusHours(4),
                eventDate.minusHours(3),
                event
        );
        service.getTimeslots().add(existingSlot);
        validRequest.setStartTime(eventDate.minusHours(3)); // Starts exactly when previous ends
        validRequest.setEndTime(eventDate.minusHours(2));


        ReservationResponseDTO response = reservationService.reserveService(1, validRequest);

        // Verify response is not null and contains correct data
        assertNotNull(response, "Response should not be null");
        assertEquals(provider.getId(), response.getProviderId(), "Provider ID should match");
        assertEquals(event.getId(), response.getEventId(), "Event ID should match");
        assertEquals(service.getId(), response.getServiceId(), "Service ID should match");
        assertEquals(validRequest.getStartTime(), response.getStartTime(), "Start time should match request");
        assertEquals(validRequest.getEndTime(), response.getEndTime(), "End time should match request");
        assertEquals(provider.getUsername(), response.getProviderEmail(), "Provider email should match");

        // Verify the service and event were saved
        verify(serviceRepository).save(service);
        verify(eventRepository).save(event);
        verify(budgetItemRepository).save(any());
        verify(timeslotRepository).save(any(Timeslot.class));
    }
}