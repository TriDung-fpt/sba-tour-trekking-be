package com.trekking.app.features.booking.exception;

import com.trekking.app.features.booking.dto.response.BookingSummaryResponse;
import java.util.List;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BookingException extends RuntimeException {
    private final BookingErrorCode errorCode;
    private final List<BookingSummaryResponse> activeHolds;

    public BookingException(BookingErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), List.of());
    }

    public BookingException(BookingErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public BookingException(BookingErrorCode errorCode, String message, List<BookingSummaryResponse> activeHolds) {
        super(message);
        this.errorCode = errorCode;
        this.activeHolds = List.copyOf(activeHolds);
    }

    public HttpStatus getStatus() {
        return errorCode.getStatus();
    }

    public String getCode() {
        return errorCode.name();
    }

    public static BookingException notFound() {
        return new BookingException(BookingErrorCode.BOOKING_NOT_FOUND);
    }

    public static BookingException insufficientSeats(int remaining) {
        return new BookingException(BookingErrorCode.INSUFFICIENT_SEATS,
                "Only " + remaining + " seat(s) left on this schedule");
    }

    public static BookingException activeBookingHolds(List<BookingSummaryResponse> activeHolds) {
        return new BookingException(BookingErrorCode.ACTIVE_BOOKING_HOLD_EXISTS,
                BookingErrorCode.ACTIVE_BOOKING_HOLD_EXISTS.getDefaultMessage(), activeHolds);
    }
}
