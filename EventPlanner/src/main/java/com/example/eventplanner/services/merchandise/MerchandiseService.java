package com.example.eventplanner.services.merchandise;

import com.example.eventplanner.dto.category.CategoryOverviewDTO;
import com.example.eventplanner.dto.category.GetAllCategoriesDTO;
import com.example.eventplanner.dto.common.AddressDTO;
import com.example.eventplanner.dto.eventType.EventTypeOverviewDTO;
import com.example.eventplanner.dto.merchandise.MerchandiseDetailDTO;
import com.example.eventplanner.dto.merchandise.MerchandiseOverviewDTO;
import com.example.eventplanner.dto.merchandise.MerchandisePhotoDTO;
import com.example.eventplanner.dto.merchandise.review.MerchandiseReviewOverviewDTO;
import com.example.eventplanner.model.event.Category;
import com.example.eventplanner.model.event.EventType;
import com.example.eventplanner.model.merchandise.Merchandise;
import com.example.eventplanner.model.merchandise.MerchandisePhoto;
import com.example.eventplanner.model.merchandise.Review;
import com.example.eventplanner.model.user.ServiceProvider;
import com.example.eventplanner.model.user.User;
import com.example.eventplanner.repositories.category.CategoryRepository;
import com.example.eventplanner.repositories.merchandise.MerchandiseRepository;
import com.example.eventplanner.repositories.user.ServiceProviderRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MerchandiseService {
    private final MerchandiseRepository merchandiseRepository;
    private final CategoryRepository categoryRepository;
    private final com.example.eventplanner.repositories.user.UserRepository userRepository;
    private final ServiceProviderRepository serviceProviderRepository;

    public List<MerchandiseOverviewDTO> getTop(int userId) {
        User currentUser = fetchUserDetails(userId);

        // User-specific details
        List<User> blockedUsers = currentUser != null ? currentUser.getBlockedUsers() : List.of();
        String userCity = currentUser != null && currentUser.getAddress() != null
                ? currentUser.getAddress().getCity()
                : null;

        // Fetch all merchandise
        List<Merchandise> allMerchandise = merchandiseRepository.findAll();

        // Step 1: Apply filters
        List<Merchandise> filteredMerchandise = allMerchandise.stream()
                // Include only available merchandise
                .filter(Merchandise::isAvailable)
                // Exclude merchandise provided by blocked users
                .filter(merchandise -> isNotBlocked(blockedUsers, serviceProviderRepository.findByMerchandiseId(merchandise.getId()).get()))
                // Filter by city (if user has a city set)
                .filter(merchandise -> isCityMatching(userCity, merchandise.getAddress().getCity()))
                .toList();

        // Step 2: Sort and limit to top 5 by rating
        return filteredMerchandise.stream()
                .sorted(Comparator.comparing(Merchandise::getRating).reversed())
                .limit(5)
                .map(this::convertToOverviewDTO)
                .toList();
    }
    private User fetchUserDetails(int userId) {
        return userRepository.findById(userId).orElse(null);
    }

    private boolean isCityMatching(String userCity, String eventCity) {
        return userCity == null || userCity.isEmpty() || userCity.equalsIgnoreCase(eventCity);
    }

    private boolean isNotBlocked(List<User> blockedUsers, User organizer) {
        return blockedUsers == null || !blockedUsers.contains(organizer);
    }

    private MerchandiseOverviewDTO convertToOverviewDTO(Merchandise merchandise) {
        MerchandiseOverviewDTO dto = new MerchandiseOverviewDTO();
        dto.setId(merchandise.getId());
        dto.setTitle(merchandise.getTitle());
        dto.setDescription(merchandise.getDescription());
        dto.setAddress(merchandise.getAddress());
        dto.setCategory(merchandise.getCategory().getTitle());
        if(merchandise.getPhotos() != null && !merchandise.getPhotos().isEmpty())
            dto.setPhotos(merchandise.getPhotos().stream().map(this::mapToMerchandisePhotoDTO).toList());
        dto.setRating(merchandise.getRating());
        dto.setType(merchandise.getClass().getSimpleName());
        dto.setPrice(merchandise.getPrice());
        return dto;
    }

    private MerchandisePhotoDTO mapToMerchandisePhotoDTO(MerchandisePhoto photo){
        MerchandisePhotoDTO dto = new MerchandisePhotoDTO();
        dto.setId(photo.getId());
        dto.setPhoto(photo.getPhoto());
        return dto;
    }

    public GetAllCategoriesDTO getAllCategories(){
        GetAllCategoriesDTO allCategoriesDTO = new GetAllCategoriesDTO();
        allCategoriesDTO.setCategories(categoryRepository.findAll().stream().map(this::convertToCategoryOverviewDTO).toList());
        return allCategoriesDTO;
    }

    private CategoryOverviewDTO convertToCategoryOverviewDTO(Category category) {
        CategoryOverviewDTO categoryDTO = new CategoryOverviewDTO();
        categoryDTO.setId(category.getId());
        categoryDTO.setTitle(category.getTitle());
        categoryDTO.setDescription(category.getDescription());
        return categoryDTO;
    }

    public MerchandiseDetailDTO getMerchandiseById(int id) {
        Merchandise merchandise = merchandiseRepository.findById(id).orElseThrow(() ->
                new RuntimeException("Merchandise with id " + id + " not found")
        );
        return mapToMerchandiseDetails(merchandise);
    }

    private MerchandiseDetailDTO mapToMerchandiseDetails(Merchandise merchandise) {
        MerchandiseDetailDTO merchandiseDetails = new MerchandiseDetailDTO();
        merchandiseDetails.setId(merchandise.getId());
        merchandiseDetails.setTitle(merchandise.getTitle());
        merchandiseDetails.setDescription(merchandise.getDescription());
        merchandiseDetails.setSpecificity(merchandise.getSpecificity());
        merchandiseDetails.setPrice(merchandise.getPrice());
        merchandiseDetails.setDiscount(merchandise.getDiscount());
        merchandiseDetails.setVisible(merchandise.isVisible());
        merchandiseDetails.setAvailable(merchandise.isAvailable());
        merchandiseDetails.setMinDuration(merchandise.getMinDuration());
        merchandiseDetails.setMaxDuration(merchandise.getMaxDuration());
        merchandiseDetails.setReservationDeadline(merchandise.getReservationDeadline());
        merchandiseDetails.setCancellationDeadline(merchandise.getCancellationDeadline());
        merchandiseDetails.setMerchandisePhotos(merchandise.getPhotos().stream().map(this::mapToMerchandisePhotoDTO).toList());
        merchandiseDetails.setReviews(merchandise.getReviews().stream().map(this::mapToMerchandiseReviewDTO).toList());

        AddressDTO addressDTO = new AddressDTO();
        addressDTO.setCity(merchandise.getAddress().getCity());
        addressDTO.setStreet(merchandise.getAddress().getStreet());
        addressDTO.setNumber(merchandise.getAddress().getNumber());
        addressDTO.setLongitude(merchandise.getAddress().getLongitude());
        addressDTO.setLatitude(merchandise.getAddress().getLatitude());
        merchandiseDetails.setAddress(addressDTO);

        CategoryOverviewDTO categoryOverviewDTO = new CategoryOverviewDTO();
        categoryOverviewDTO.setId(merchandise.getCategory().getId());
        categoryOverviewDTO.setTitle(merchandise.getCategory().getTitle());
        categoryOverviewDTO.setDescription(merchandise.getCategory().getDescription());
        categoryOverviewDTO.setPending(merchandise.getCategory().isPending());
        merchandiseDetails.setCategory(categoryOverviewDTO);

        merchandiseDetails.setEventTypes(merchandise.getEventTypes().stream().map(this::mapToEventTypeOverviewDTO).toList());
        merchandiseDetails.setRating(merchandise.getRating());
        return merchandiseDetails;
    }

    private EventTypeOverviewDTO mapToEventTypeOverviewDTO(EventType eventType) {
        EventTypeOverviewDTO dto = new EventTypeOverviewDTO();
        dto.setId(eventType.getId());
        dto.setTitle(eventType.getTitle());
        dto.setDescription(eventType.getDescription());
        dto.setActive(eventType.isActive());
        return dto;
    }

    private MerchandiseReviewOverviewDTO mapToMerchandiseReviewDTO(Review review) {
        MerchandiseReviewOverviewDTO dto = new MerchandiseReviewOverviewDTO();
        dto.setReviewersUsername(review.getReviewer().getUsername());
        dto.setComment(review.getComment());
        dto.setRating(review.getRating());
        return dto;
    }




}
