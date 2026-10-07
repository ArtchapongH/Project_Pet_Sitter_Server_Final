package com.techup.pet_sitter.controller;

import com.techup.pet_sitter.dto.BookingAdminListItem;
import com.techup.pet_sitter.dto.BookingRequest;
import com.techup.pet_sitter.dto.BookingResponse;
import com.techup.pet_sitter.dto.BookingStatusRequest;
import com.techup.pet_sitter.dto.SitterBookingResponse;
import com.techup.pet_sitter.entity.Booking;
import com.techup.pet_sitter.entity.User;
import com.techup.pet_sitter.repository.BookingPetRepository;
import com.techup.pet_sitter.repository.BookingRepository;
import com.techup.pet_sitter.repository.PaymentRepository;
import com.techup.pet_sitter.repository.PetRepository;
import com.techup.pet_sitter.repository.UserRepository;
import com.techup.pet_sitter.service.OwnerProfileRules;
import com.techup.pet_sitter.service.BookingAdminService;
import com.techup.pet_sitter.service.SitterApprovalService;
import com.techup.pet_sitter.service.SitterBookingService;
import com.techup.pet_sitter.security.JwtUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingRepository bookings;
    private final UserRepository users;
    private final SitterApprovalService approvals;
    private final SitterBookingService sitterBookings;
    private final PaymentRepository payments;
    private final PetRepository pets;
    private final BookingPetRepository bookingPets;
    private final com.techup.pet_sitter.repository.SitterProfileRepository profiles;
    private final BookingAdminService bookingAdminService;

    @org.springframework.beans.factory.annotation.Value("${stripe.secret.key:}")
    private String stripeSecretKey;

    public BookingController(BookingRepository bookings, UserRepository users, SitterApprovalService approvals,
                             SitterBookingService sitterBookings, PaymentRepository payments,
                             PetRepository pets, BookingPetRepository bookingPets,
                             com.techup.pet_sitter.repository.SitterProfileRepository profiles,
                             BookingAdminService bookingAdminService) {
        this.bookings = bookings;
        this.users = users;
        this.approvals = approvals;
        this.sitterBookings = sitterBookings;
        this.payments = payments;
        this.pets = pets;
        this.bookingPets = bookingPets;
        this.profiles = profiles;
        this.bookingAdminService = bookingAdminService;
    }

    @GetMapping("/sitter")
    List<SitterBookingResponse> listForSitter(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) String query,
                                              @RequestParam(required = false) LocalDate from,
                                              @RequestParam(required = false) LocalDate to) {
        return sitterBookings.list(JwtUser.id(jwt), query, from, to);
    }

    @GetMapping("/sitter/{id}")
    SitterBookingResponse getForSitter(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return sitterBookings.get(JwtUser.id(jwt), id);
    }

    @PatchMapping("/sitter/{id}/status")
    SitterBookingResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                       @RequestBody BookingStatusRequest request) {
        return sitterBookings.changeStatus(JwtUser.id(jwt), id, request.status());
    }

    @PostMapping
    @Transactional
    public BookingResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody BookingRequest request
    ) {
        if (request.sitterId() == null || request.startDate() == null || request.endDate() == null
                || request.startTime() == null || request.endTime() == null || request.duration() == null
                || request.totalPrice() == null || request.contactName() == null || request.contactEmail() == null
                || request.contactPhone() == null || !List.of("hours", "Day").contains(request.durationUnit())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking details are incomplete");
        }
        if (request.endDate().isBefore(request.startDate()) || request.duration().signum() <= 0
                || request.totalPrice().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking dates, duration or price are invalid");
        }
        User owner = users.findById(JwtUser.id(jwt))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found"));
        OwnerProfileRules.requireNotBanned(owner);

        com.techup.pet_sitter.entity.SitterProfile sitterProfile = approvals.requireBookable(request.sitterId());

        Booking booking = new Booking();
        booking.setOwner(owner);
        booking.setSitter(sitterProfile);
        booking.setStartDate(request.startDate());
        booking.setEndDate(request.endDate());
        booking.setStartTime(request.startTime());
        booking.setEndTime(request.endTime());
        booking.setDuration(request.duration());
        booking.setDurationUnit(request.durationUnit());
        booking.setContactName(request.contactName());
        booking.setContactEmail(request.contactEmail());
        booking.setContactPhone(request.contactPhone());
        booking.setAdditionalMessage(request.additionalMessage());
        booking.setTotalPrice(request.totalPrice());
        booking.setPaymentMethod(request.paymentMethod() == null ? "credit_card" : request.paymentMethod());
        booking.setStatus("waiting_confirm");

        String txnNo = String.valueOf((long) (Math.random() * 900000L + 100000L));
        booking.setTransactionNo(txnNo);
        Booking saved = bookings.save(booking);

        // Link pets to BookingPet
        if (request.petIds() != null && !request.petIds().isEmpty()) {
            for (Long petId : request.petIds()) {
                pets.findById(petId).ifPresent(p -> {
                    com.techup.pet_sitter.entity.BookingPet bp = new com.techup.pet_sitter.entity.BookingPet();
                    bp.setId(new com.techup.pet_sitter.entity.BookingPet.BookingPetId(saved.getId(), p.getId()));
                    bp.setBooking(saved);
                    bp.setPet(p);
                    bookingPets.save(bp);
                });
            }
        }

        // Record Payment
        com.techup.pet_sitter.entity.Payment payment = new com.techup.pet_sitter.entity.Payment();
        payment.setBooking(saved);
        payment.setAmount(saved.getTotalPrice());
        payment.setPaidAt(java.time.OffsetDateTime.now());
        payment.setStatus("success");
        if (request.cardOwnerName() != null && !request.cardOwnerName().isBlank()) {
            payment.setCardOwnerName(request.cardOwnerName().trim());
        }
        if (request.cardNumber() != null && !request.cardNumber().isBlank()) {
            String digits = request.cardNumber().replaceAll("\\D", "");
            payment.setCardLast4(digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits);
        }

        // Real Stripe Integration
        if (stripeSecretKey != null && !stripeSecretKey.isBlank()) {
            try {
                com.stripe.Stripe.apiKey = stripeSecretKey.trim();
                long amountInSubunits = saved.getTotalPrice().multiply(new java.math.BigDecimal(100)).longValue();
                com.stripe.param.PaymentIntentCreateParams params = com.stripe.param.PaymentIntentCreateParams.builder()
                        .setAmount(amountInSubunits)
                        .setCurrency("thb")
                        .setDescription("Pet Sitter Booking #" + saved.getTransactionNo() + " - " + saved.getContactName())
                        .setPaymentMethod("pm_card_visa")
                        .setConfirm(true)
                        .setAutomaticPaymentMethods(
                                com.stripe.param.PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                        .setEnabled(true)
                                        .setAllowRedirects(com.stripe.param.PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                        .build()
                        )
                        .putMetadata("bookingId", String.valueOf(saved.getId()))
                        .putMetadata("transactionNo", saved.getTransactionNo())
                        .putMetadata("customerName", saved.getContactName())
                        .build();
                com.stripe.model.PaymentIntent intent = com.stripe.model.PaymentIntent.create(params);
                payment.setPaymentToken(intent.getId());
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(BookingController.class).warn("Stripe PaymentIntent note: {}", e.getMessage());
            }
        }

        payments.save(payment);

        return new BookingResponse(saved.getId(), saved.getStatus(), saved.getTransactionNo());
    }

    @GetMapping("/admin/sitter/{sitterId}")
    public List<BookingAdminListItem> listBySitter(@PathVariable UUID sitterId) {
        return bookingAdminService.listForSitter(sitterId);
    }
}
