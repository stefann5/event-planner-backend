package com.example.eventplanner.repositories.event;
import com.example.eventplanner.model.common.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import com.example.eventplanner.model.event.Event;

@Repository
public interface EventRepository extends JpaRepository<Event, Integer>, JpaSpecificationExecutor<Event> {
    Page<Event> findByOrganizerId(int organizerId, Pageable pageable);
    Event findByReviewsContaining(Review review);
}
