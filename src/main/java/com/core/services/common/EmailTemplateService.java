package com.core.services.common;

import java.util.Locale;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EmailTemplateService {

	private final TemplateEngine templateEngine;

	public String render(String templateName, String modelKey, Object data) {

		if (templateName == null || templateName.isBlank()) {
			System.err.println("Template name must not be null or empty");
		}

		if (modelKey == null || modelKey.isBlank()) {
			System.err.println("Model key must not be null or empty");
		}

		if (data == null) {
			System.err.println("Template data must not be null");
		}

		Context context = new Context(Locale.ENGLISH);
		context.setVariable(modelKey, data);

		try {
			return templateEngine.process(templateName, context);
		} catch (Exception ex) {
			System.err.println("Failed to render email template: " + templateName);
			System.err.println(ex.getMessage());
			return null;
		}
	}
}
