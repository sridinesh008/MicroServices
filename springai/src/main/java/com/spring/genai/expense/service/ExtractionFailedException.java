package com.spring.genai.expense.service;

public class ExtractionFailedException extends RuntimeException {

	public ExtractionFailedException(String message, Throwable cause) {
		super(message, cause);
	}

}
