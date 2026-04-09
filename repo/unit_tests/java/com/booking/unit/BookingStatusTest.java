package com.booking.unit;

import com.booking.domain.BookingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class BookingStatusTest {

    @ParameterizedTest
    @CsvSource({
        "PENDING,CONFIRMED,true",   "PENDING,CANCELLED,true",
        "PENDING,COMPLETED,false",  "PENDING,IN_PROGRESS,false",
        "CONFIRMED,IN_PROGRESS,true","CONFIRMED,CANCELLED,true",
        "CONFIRMED,COMPLETED,false",
        "IN_PROGRESS,COMPLETED,true","IN_PROGRESS,CANCELLED,true",
        "IN_PROGRESS,CONFIRMED,false",
        "COMPLETED,CANCELLED,false","COMPLETED,PENDING,false",
        "CANCELLED,PENDING,false",  "CANCELLED,CONFIRMED,false"
    })
    void testTransitions(String from, String to, boolean expected) {
        assertEquals(expected, BookingStatus.valueOf(from).canTransitionTo(BookingStatus.valueOf(to)));
    }
}
