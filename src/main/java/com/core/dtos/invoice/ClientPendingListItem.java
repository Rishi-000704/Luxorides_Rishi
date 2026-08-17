package com.core.dtos.invoice;

import java.math.BigDecimal;

public record ClientPendingListItem(

        String clientId,

        String clientName,

        String phoneNumber,

        String email,

        BigDecimal pendingAmount

) {

}