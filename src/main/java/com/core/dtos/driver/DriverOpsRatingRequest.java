package com.core.dtos.driver;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DriverOpsRatingRequest(
		@NotNull @Min(1) @Max(5) Integer stars,
		String comment
) {
}
